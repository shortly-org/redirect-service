package io.shortly.redirect.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.shortly.redirect.config.CacheProperties;
import io.shortly.redirect.exception.SnapshotUnavailableException;
import io.shortly.redirect.model.LinkSnapshot;
import io.shortly.redirect.model.LinkState;
import io.shortly.redirect.repository.LinkRepository;
import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import org.jspecify.annotations.Nullable;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

/**
 * The in-process copy of links, written by the change feed and by replica reads on a miss; the
 * higher version wins. While the feed is stale, entries are served for {@code fallbackTtl} at most.
 */
@Service
public class LinkCacheService {

    private final Cache<String, Entry> entries;
    private final LinkRepository repository;
    private final CacheProperties props;
    private final InstantSource time;

    private volatile Instant lastFeedPoll = Instant.EPOCH;

    public LinkCacheService(
            LinkRepository repository,
            CacheProperties props,
            MeterRegistry meters,
            InstantSource time) {
        this.repository = repository;
        this.props = props;
        this.time = time;
        this.entries = Caffeine.newBuilder().maximumSize(props.maxSize()).build();
        Gauge.builder("shortly.cache.size", entries, Cache::estimatedSize).register(meters);
        Gauge.builder("shortly.feed.age_seconds", this, LinkCacheService::feedAgeSeconds)
                .description("Seconds since the last successful change-feed poll")
                .register(meters);
    }

    /** The current snapshot for a code, asking the replica at most once per miss. */
    public LinkSnapshot resolve(String code) {
        Entry entry = entries.getIfPresent(code);
        if (entry != null && isFresh(entry)) {
            return entry.snapshot();
        }
        return entries.asMap().compute(code, this::freshOrLoaded).snapshot();
    }

    public void apply(LinkSnapshot fromFeed) {
        Entry cached = entries.policy().getIfPresentQuietly(fromFeed.code());
        if (cached != null && cached.snapshot().equals(fromFeed)) {
            return;
        }
        entries.asMap().compute(fromFeed.code(), (code, current) -> newer(current, fromFeed));
    }

    public void markFeedFresh() {
        lastFeedPoll = time.instant();
    }

    public boolean isFeedStale() {
        return lastFeedPoll.plus(props.feedStaleAfter()).isBefore(time.instant());
    }

    private Entry freshOrLoaded(String code, @Nullable Entry cached) {
        if (cached != null && isFresh(cached)) {
            return cached;
        }
        try {
            LinkSnapshot loaded =
                    repository.findByCode(code).orElseGet(() -> LinkSnapshot.notFound(code));
            return newer(cached, loaded);
        } catch (DataAccessException | BulkheadFullException | CallNotPermittedException e) {
            if (cached == null) {
                throw new SnapshotUnavailableException(e);
            }
            return new Entry(cached.snapshot(), time.instant(), props.fallbackTtl());
        }
    }

    /** Whichever has the higher version; the incoming snapshot wins a tie. */
    private Entry newer(@Nullable Entry cached, LinkSnapshot incoming) {
        LinkSnapshot snapshot =
                cached == null || incoming.version() >= cached.snapshot().version()
                        ? incoming
                        : cached.snapshot();
        if (snapshot.state() == LinkState.NOT_FOUND) {
            return new Entry(snapshot, time.instant(), props.negativeTtl());
        } else {
            return new Entry(snapshot, time.instant(), props.ttl());
        }
    }

    private boolean isFresh(Entry entry) {
        Duration ttl = isFeedStale() ? props.fallbackTtl() : entry.ttl();
        return time.instant().isBefore(entry.storedAt().plus(ttl));
    }

    private double feedAgeSeconds() {
        return Duration.between(lastFeedPoll, time.instant()).toMillis() / 1000.0;
    }

    /** A snapshot, when it was stored, and how long it may be served. */
    private record Entry(LinkSnapshot snapshot, Instant storedAt, Duration ttl) {}
}
