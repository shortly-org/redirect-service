package io.shortly.redirect.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.shortly.redirect.MutableTime;
import io.shortly.redirect.TestProperties;
import io.shortly.redirect.config.CacheProperties;
import io.shortly.redirect.model.LinkSnapshot;
import io.shortly.redirect.model.LinkState;
import io.shortly.redirect.repository.LinkRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

class LinkCacheServiceTest {

    private static final Instant T0 = Instant.parse("2026-10-20T10:00:00Z");

    private final CacheProperties cacheProps = TestProperties.cache();
    private final MutableTime time = new MutableTime(T0);
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final AtomicInteger loads = new AtomicInteger();
    private volatile Supplier<Optional<LinkSnapshot>> answer = () -> Optional.of(active("abc", 1));
    private final LinkRepository repository = mock(LinkRepository.class);
    private final LinkCacheService cache =
            new LinkCacheService(repository, cacheProps, registry, time);

    @BeforeEach
    void setUp() {
        when(repository.findByCode(anyString()))
                .thenAnswer(
                        _ -> {
                            loads.incrementAndGet();
                            return answer.get();
                        });
        cache.markFeedFresh();
    }

    @Test
    void olderLoadNeverOverwritesNewerFeedState() {
        cache.apply(new LinkSnapshot("abc", "https://a/", LinkState.INACTIVE, 8));
        answer = () -> Optional.of(active("abc", 7));
        advanceWithFreshFeed(Duration.ofMinutes(11));

        LinkSnapshot got = cache.resolve("abc");

        assertThat(loads).hasValue(1);
        assertThat(got.state()).isEqualTo(LinkState.INACTIVE);
        assertThat(got.version()).isEqualTo(8);
    }

    @Test
    void feedEventOlderThanCachedIsIgnored() {
        cache.apply(new LinkSnapshot("abc", "https://v3/", LinkState.ACTIVE, 3));
        cache.apply(new LinkSnapshot("abc", "https://v2/", LinkState.INACTIVE, 2));

        assertThat(cache.resolve("abc").targetUrl()).isEqualTo("https://v3/");
        assertThat(loads).hasValue(0);
    }

    @Test
    void servesStaleEntryAndRetriesWithinASecondWhenReplicaFails() {
        assertThat(cache.resolve("abc").version()).isEqualTo(1);
        answer =
                () -> {
                    throw new DataAccessResourceFailureException("replica down");
                };
        advanceWithFreshFeed(Duration.ofMinutes(11));

        assertThat(cache.resolve("abc").version()).isEqualTo(1);

        advanceWithFreshFeed(Duration.ofMillis(1500));
        cache.resolve("abc");
        assertThat(loads).hasValue(3);
    }

    private void advanceWithFreshFeed(Duration by) {
        time.advance(by);
        cache.markFeedFresh();
    }

    private static LinkSnapshot active(String code, int version) {
        return new LinkSnapshot(code, "https://example.org/" + version, LinkState.ACTIVE, version);
    }
}
