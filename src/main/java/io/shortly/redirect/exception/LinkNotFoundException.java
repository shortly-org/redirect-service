package io.shortly.redirect.exception;

public class LinkNotFoundException extends RuntimeException {

    public LinkNotFoundException() {
        super("link not found", null, false, false);
    }
}
