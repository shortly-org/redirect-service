package io.shortly.redirect.exception;

public class LinkInactiveException extends RuntimeException {

    public LinkInactiveException() {
        super("link inactive", null, false, false);
    }
}
