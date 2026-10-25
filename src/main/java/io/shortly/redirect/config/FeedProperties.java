package io.shortly.redirect.config;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * @param overlapWindow how far before the previous poll's start rows are re-read by {@code
 *     updated_at}
 * @param batchLimit the most changes one poll applies
 */
@ConfigurationProperties("shortly.redirect.feed")
@Validated
public record FeedProperties(
        @DefaultValue("5s") @NotNull Duration overlapWindow,
        @DefaultValue("10000") @Positive int batchLimit) {}
