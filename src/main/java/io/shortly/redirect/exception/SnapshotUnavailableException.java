package io.shortly.redirect.exception;

/** The code is not cached and the replica could not be asked. */
public class SnapshotUnavailableException extends RuntimeException {

    public SnapshotUnavailableException(Throwable cause) {
        super("link snapshot unavailable", cause);
    }
}
