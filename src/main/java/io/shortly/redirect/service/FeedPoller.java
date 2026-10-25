package io.shortly.redirect.service;

import io.shortly.redirect.config.FeedProperties;
import io.shortly.redirect.model.LinkChange;
import io.shortly.redirect.repository.LinkChangeRepository;
import java.time.Instant;
import java.time.InstantSource;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.dao.DataAccessException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Follows {@code link_snapshot} by change number into the cache, and on every poll re-reads the
 * rows changed since shortly before the previous poll, to catch changes that committed late. Also
 * the {@code feed} health check: ready after the first successful poll.
 */
@Component("feed")
public class FeedPoller implements HealthIndicator {

    private static final Logger log = LoggerFactory.getLogger(FeedPoller.class);
    private static final long NOT_STARTED = -1;

    private final LinkChangeRepository repository;
    private final LinkCacheService linkCacheService;
    private final FeedProperties props;
    private final InstantSource time;

    private volatile long appliedSeq = NOT_STARTED;

    private volatile @Nullable Instant previousPollStart;

    public FeedPoller(
            LinkChangeRepository repository,
            LinkCacheService linkCacheService,
            FeedProperties props,
            InstantSource time) {
        this.repository = repository;
        this.linkCacheService = linkCacheService;
        this.props = props;
        this.time = time;
    }

    @Scheduled(fixedDelayString = "${shortly.redirect.feed.poll-interval}")
    public void pollOnce() {
        try {
            Instant pollStart = time.instant();
            if (appliedSeq == NOT_STARTED) {
                appliedSeq = repository.maxSeq();
            }
            reapplyRecentChanges();
            applyNewChanges();
            previousPollStart = pollStart;
            linkCacheService.markFeedFresh();
        } catch (DataAccessException e) {
            log.warn("change feed poll failed: {}", e.toString());
        }
    }

    private void reapplyRecentChanges() {
        Instant previous = previousPollStart;
        if (previous == null) {
            return;
        }
        Instant since = previous.minus(props.overlapWindow());
        List<LinkChange> recent = repository.changedSince(since, appliedSeq);
        for (LinkChange change : recent) {
            linkCacheService.apply(change.snapshot());
        }
    }

    private void applyNewChanges() {
        List<LinkChange> changes = repository.changesAfter(appliedSeq, props.batchLimit());
        for (LinkChange change : changes) {
            linkCacheService.apply(change.snapshot());
            appliedSeq = change.changeSeq();
        }
    }

    @Override
    public Health health() {
        Health.Builder health = isReady() ? Health.up() : Health.down();
        return health.withDetail("lastSeq", appliedSeq)
                .withDetail("stale", linkCacheService.isFeedStale())
                .build();
    }

    public boolean isReady() {
        return previousPollStart != null;
    }
}
