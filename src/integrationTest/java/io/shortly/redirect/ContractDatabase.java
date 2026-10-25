package io.shortly.redirect;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** A Postgres holding nothing but {@code contract/link-contract.sql}. */
public class ContractDatabase {

    private static final Path CONTRACT = Path.of("contract/link-contract.sql");

    private final DataSource dataSource;
    private final JdbcClient jdbc;

    public ContractDatabase(PostgreSQLContainer postgres) {
        this.dataSource =
                new DriverManagerDataSource(
                        postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        this.jdbc = JdbcClient.create(dataSource);
        applyContract();
    }

    public JdbcClient jdbc() {
        return jdbc;
    }

    public void truncate() {
        jdbc.sql("TRUNCATE links").update();
    }

    /** Inserts or changes a link the way the dashboard does, with the next change number. */
    public void upsertLink(String code, String targetUrl, String status, int version) {
        jdbc.sql(
                        """
                        INSERT INTO links (code, target_url, status, version) VALUES (?, ?, ?, ?)
                        ON CONFLICT (code) DO UPDATE SET target_url = excluded.target_url, status = excluded.status,
                            version = excluded.version, change_seq = nextval('link_change_seq'), updated_at = now()
                        """)
                .params(code, targetUrl, status, version)
                .update();
    }

    private void applyContract() {
        String contract;
        try {
            contract = Files.readString(CONTRACT);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        new JdbcTemplate(dataSource).execute(contract);
    }
}
