package io.shortly.redirect.config;

import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * @param queueSize the most clicks waiting for Kafka; beyond it new clicks are dropped and counted
 */
@ConfigurationProperties("shortly.redirect.clicks")
@Validated
public record ClickProperties(@DefaultValue("100000") @Positive int queueSize) {}
