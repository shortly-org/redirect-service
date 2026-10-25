package io.shortly.redirect.controller;

import io.shortly.redirect.service.RedirectService;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

@RestController
class RedirectController {

    private final RedirectService redirectService;

    RedirectController(RedirectService redirectService) {
        this.redirectService = redirectService;
    }

    @GetMapping("/{code}")
    ResponseEntity<Void> follow(
            @PathVariable String code,
            @RequestHeader("CloudFront-Viewer-Country") @Nullable String country,
            @RequestHeader(HttpHeaders.REFERER) @Nullable String referer) {
        return redirect(redirectService.follow(code, country, referer));
    }

    /** Answered like GET, but never counted as a click. */
    @RequestMapping(path = "/{code}", method = RequestMethod.HEAD)
    ResponseEntity<Void> peek(@PathVariable String code) {
        return redirect(redirectService.peek(code));
    }

    private static ResponseEntity<Void> redirect(String location) {
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, location)
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .header("Referrer-Policy", "no-referrer")
                .build();
    }
}
