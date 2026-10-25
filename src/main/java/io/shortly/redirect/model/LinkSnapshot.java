package io.shortly.redirect.model;

import org.jspecify.annotations.Nullable;

public record LinkSnapshot(String code, @Nullable String targetUrl, LinkState state, int version) {

    /** A cached "not found", replaced by any real version from the feed. */
    public static LinkSnapshot notFound(String code) {
        return new LinkSnapshot(code, null, LinkState.NOT_FOUND, 0);
    }
}
