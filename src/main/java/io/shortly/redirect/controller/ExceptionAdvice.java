package io.shortly.redirect.controller;

import io.shortly.redirect.exception.LinkInactiveException;
import io.shortly.redirect.exception.LinkNotFoundException;
import io.shortly.redirect.exception.SnapshotUnavailableException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Turns the service's exceptions into responses that are never cached. */
@RestControllerAdvice
class ExceptionAdvice {

    private static final String NO_STORE = "private, no-store";

    private static final String NOT_FOUND_PAGE = read("pages/404.html");
    private static final String GONE_PAGE = read("pages/410.html");

    @ExceptionHandler(LinkNotFoundException.class)
    ResponseEntity<String> notFound() {
        return page(HttpStatus.NOT_FOUND, NOT_FOUND_PAGE);
    }

    @ExceptionHandler(LinkInactiveException.class)
    ResponseEntity<String> gone() {
        return page(HttpStatus.GONE, GONE_PAGE);
    }

    @ExceptionHandler(SnapshotUnavailableException.class)
    ResponseEntity<Void> unavailable() {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .header(HttpHeaders.RETRY_AFTER, "1")
                .header(HttpHeaders.CACHE_CONTROL, NO_STORE)
                .build();
    }

    private static ResponseEntity<String> page(HttpStatus status, String html) {
        return ResponseEntity.status(status)
                .contentType(MediaType.TEXT_HTML)
                .header(HttpHeaders.CACHE_CONTROL, NO_STORE)
                .header(
                        "Content-Security-Policy",
                        "default-src 'none'; style-src 'unsafe-inline'; frame-ancestors 'none'")
                .body(html);
    }

    private static String read(String path) {
        try {
            return new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("missing error page " + path, e);
        }
    }
}
