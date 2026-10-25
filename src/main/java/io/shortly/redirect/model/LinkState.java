package io.shortly.redirect.model;

import org.jspecify.annotations.Nullable;

/** What the redirect does with a code: redirect, refuse (410) or not found (404). */
public enum LinkState {
    ACTIVE,
    INACTIVE,
    NOT_FOUND;

    /**
     * The state of a status from {@code link_snapshot}; deleted and unknown statuses are NOT_FOUND.
     */
    public static LinkState parse(@Nullable String status) {
        for (LinkState state : values()) {
            if (state.name().equals(status)) {
                return state;
            }
        }
        return NOT_FOUND;
    }
}
