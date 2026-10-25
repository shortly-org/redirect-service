package io.shortly.redirect.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.shortly.redirect.AbstractIntegrationTest;
import io.shortly.redirect.ContractDatabase;
import io.shortly.redirect.model.LinkState;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class FeedPollerIntegrationTest extends AbstractIntegrationTest {

    @Autowired private ContractDatabase db;
    @Autowired private LinkCacheService cache;
    @Autowired private FeedPoller poller;

    @BeforeEach
    void emptyContractTables() {
        db.truncate();
    }

    @Test
    void changeVisibleWithinOnePollInterval() {
        await().atMost(Duration.ofSeconds(5)).until(poller::isReady);
        db.upsertLink("feedOnly1", "https://example.org/", "ACTIVE", 1);
        assertThat(cache.resolve("feedOnly1").state()).isEqualTo(LinkState.ACTIVE);

        db.upsertLink("feedOnly1", "https://example.org/", "INACTIVE", 2);

        await().atMost(Duration.ofSeconds(3))
                .untilAsserted(
                        () ->
                                assertThat(cache.resolve("feedOnly1").state())
                                        .isEqualTo(LinkState.INACTIVE));
    }
}
