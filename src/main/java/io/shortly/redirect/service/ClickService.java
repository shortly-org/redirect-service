package io.shortly.redirect.service;

import io.shortly.redirect.model.ClickEvent;
import io.shortly.redirect.model.ClickEvent.Outcome;
import io.shortly.redirect.producer.ClickProducer;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.InstantSource;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

/** Turns a served or blocked request into a click: no IP, only the referrer's host. */
@Service
public class ClickService {

    private static final Pattern COUNTRY_CODE = Pattern.compile("^[A-Z]{2}$");

    private static final int MAX_HOST_LENGTH = 253;

    private static final Pattern IP_ADDRESS = Pattern.compile("^(\\[.*]|[0-9.]+)$");

    private final ClickProducer clickProducer;
    private final InstantSource time;

    public ClickService(ClickProducer clickProducer, InstantSource time) {
        this.clickProducer = clickProducer;
        this.time = time;
    }

    public void recordClick(
            String code,
            Outcome outcome,
            @Nullable String countryHeader,
            @Nullable String refererHeader) {
        clickProducer.send(
                new ClickEvent(
                        UUID.randomUUID().toString(),
                        code,
                        time.millis(),
                        outcome,
                        countryCode(countryHeader),
                        refererDomain(refererHeader)));
    }

    private static @Nullable String countryCode(@Nullable String header) {
        if (header == null) {
            return null;
        }
        String code = header.trim().toUpperCase(Locale.ROOT);
        return COUNTRY_CODE.matcher(code).matches() ? code : null;
    }

    /** The Referer's host, or null when there is none or it is an IP address. */
    private static @Nullable String refererDomain(@Nullable String header) {
        if (header == null) {
            return null;
        }
        String host;
        try {
            host = new URI(header.trim()).getHost();
        } catch (URISyntaxException _) {
            return null;
        }
        if (host == null || host.length() > MAX_HOST_LENGTH || IP_ADDRESS.matcher(host).matches()) {
            return null;
        }
        return host;
    }
}
