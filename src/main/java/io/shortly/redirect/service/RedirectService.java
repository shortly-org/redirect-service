package io.shortly.redirect.service;

import io.shortly.redirect.exception.LinkInactiveException;
import io.shortly.redirect.exception.LinkNotFoundException;
import io.shortly.redirect.exception.SnapshotUnavailableException;
import io.shortly.redirect.model.ClickEvent.Outcome;
import io.shortly.redirect.model.LinkSnapshot;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

/**
 * Returns the target of an active link, or throws {@link LinkNotFoundException}, {@link
 * LinkInactiveException} or {@link SnapshotUnavailableException}.
 */
@Service
public class RedirectService {

    private static final Pattern CODE_SHAPE = Pattern.compile("^[0-9A-Za-z]{9}$");

    private final LinkCacheService linkCacheService;
    private final ClickService clickService;

    public RedirectService(LinkCacheService linkCacheService, ClickService clickService) {
        this.linkCacheService = linkCacheService;
        this.clickService = clickService;
    }

    /** The target; the request counts as a click, served or blocked. */
    public String follow(String code, @Nullable String country, @Nullable String referer) {
        LinkSnapshot link = findLink(code);
        recordClick(link, country, referer);
        return targetUrl(link);
    }

    /** The same answer as {@link #follow}, without counting a click. */
    public String peek(String code) {
        LinkSnapshot link = findLink(code);
        return targetUrl(link);
    }

    private LinkSnapshot findLink(String code) {
        if (!CODE_SHAPE.matcher(code).matches()) {
            throw new LinkNotFoundException();
        }
        return linkCacheService.resolve(code);
    }

    private void recordClick(
            LinkSnapshot link, @Nullable String country, @Nullable String referer) {
        switch (link.state()) {
            case ACTIVE -> clickService.recordClick(link.code(), Outcome.SERVED, country, referer);
            case INACTIVE ->
                    clickService.recordClick(link.code(), Outcome.BLOCKED, country, referer);
            case NOT_FOUND -> {}
        }
    }

    private String targetUrl(LinkSnapshot link) {
        return switch (link.state()) {
            case ACTIVE -> link.targetUrl();
            case INACTIVE -> throw new LinkInactiveException();
            case NOT_FOUND -> throw new LinkNotFoundException();
        };
    }
}
