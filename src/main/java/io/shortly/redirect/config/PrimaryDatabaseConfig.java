package io.shortly.redirect.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** The primary-database pool the change feed reads; everything else uses the replica pool. */
@Configuration
public class PrimaryDatabaseConfig {

    public static final String PRIMARY_DATA_SOURCE = "primaryDataSource";

    @Bean(name = PRIMARY_DATA_SOURCE, defaultCandidate = false)
    @ConfigurationProperties("shortly.redirect.primary-datasource")
    HikariDataSource primaryDataSource() {
        return DataSourceBuilder.create().type(HikariDataSource.class).build();
    }
}
