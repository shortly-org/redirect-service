package io.shortly.redirect;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * The Postgres holding the link contract and the Kafka clicks go to, started once, and the one
 * context every test shares.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestBeans.class)
public abstract class AbstractIntegrationTest {

    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("postgres:17-alpine").withDatabaseName("contract");

    protected static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka-native:3.9.1");

    static {
        POSTGRES.start();
        KAFKA.start();
    }

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("shortly.redirect.primary-datasource.jdbc-url", POSTGRES::getJdbcUrl);
        registry.add("shortly.redirect.primary-datasource.username", POSTGRES::getUsername);
        registry.add("shortly.redirect.primary-datasource.password", POSTGRES::getPassword);
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
    }
}
