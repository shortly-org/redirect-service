package io.shortly.redirect.repository;

import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.shortly.redirect.model.LinkSnapshot;
import io.shortly.redirect.model.LinkState;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Cache misses: single links from the {@code link_snapshot} view, read from a replica. */
@Repository
public class LinkRepository {

    private final JdbcClient replica;

    public LinkRepository(JdbcClient replica) {
        this.replica = replica;
    }

    /** Empty means "no such code"; an exception means "could not ask". */
    @CircuitBreaker(name = "replica")
    @Bulkhead(name = "replica")
    public Optional<LinkSnapshot> findByCode(String code) {
        return replica.sql(
                        "SELECT code, target_url, status, version FROM link_snapshot WHERE code = ?")
                .param(code)
                .query((row, _) -> snapshot(row))
                .optional();
    }

    static LinkSnapshot snapshot(ResultSet row) throws SQLException {
        return new LinkSnapshot(
                row.getString("code"),
                row.getString("target_url"),
                LinkState.parse(row.getString("status")),
                row.getInt("version"));
    }
}
