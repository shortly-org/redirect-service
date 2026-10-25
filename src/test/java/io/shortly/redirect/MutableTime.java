package io.shortly.redirect;

import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;

/** A clock tests move by hand. */
public final class MutableTime implements InstantSource {

    private volatile Instant now;

    public MutableTime(Instant start) {
        this.now = start;
    }

    public void advance(Duration by) {
        now = now.plus(by);
    }

    @Override
    public Instant instant() {
        return now;
    }
}
