package io.shortly.redirect;

import java.security.Security;
import java.time.InstantSource;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class RedirectApplication {

    @Bean
    InstantSource time() {
        return InstantSource.system();
    }

    public static void main(String[] args) {
        // Aurora endpoints move on failover; AWS recommends a DNS cache TTL under 30 s.
        Security.setProperty("networkaddress.cache.ttl", "5");
        Security.setProperty("networkaddress.cache.negative.ttl", "1");
        SpringApplication.run(RedirectApplication.class, args);
    }
}
