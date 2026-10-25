package io.shortly.redirect.model;

import org.jspecify.annotations.Nullable;

/** One click, published to the {@code clicks} topic as JSON. */
public record ClickEvent(
        String id,
        String code,
        long at,
        Outcome outcome,
        @Nullable String country,
        @Nullable String ref) {

    public enum Outcome {
        SERVED,
        BLOCKED
    }
}
