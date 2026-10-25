package io.shortly.redirect;

import io.shortly.redirect.config.CacheProperties;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

/** The production defaults of the properties records. */
public final class TestProperties {

    private static final Binder NO_PROPERTIES = new Binder(new MapConfigurationPropertySource());

    private TestProperties() {}

    public static CacheProperties cache() {
        return NO_PROPERTIES.bindOrCreate("shortly.redirect.cache", CacheProperties.class);
    }
}
