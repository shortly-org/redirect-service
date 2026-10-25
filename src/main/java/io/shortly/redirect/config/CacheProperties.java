package io.shortly.redirect.config;

import jakarta.validation.constraints.Positive;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * @param ttl how long a cached link is served
 * @param fallbackTtl how long a cached link is served while the change feed is stale or the replica
 *     cannot be asked
 * @param negativeTtl how long a cached "not found" is served
 * @param maxSize the most links the cache holds
 * @param feedStaleAfter how old the last successful feed poll may be before the feed is stale
 */
@ConfigurationProperties("shortly.redirect.cache")
@Validated
public record CacheProperties(
        @DefaultValue("10m") Duration ttl,
        @DefaultValue("1s") Duration fallbackTtl,
        @DefaultValue("30s") Duration negativeTtl,
        @DefaultValue("500000") @Positive long maxSize,
        @DefaultValue("2s") Duration feedStaleAfter) {}
