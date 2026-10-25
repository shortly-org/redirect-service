package io.shortly.redirect.repository;

import io.shortly.redirect.config.PrimaryDatabaseConfig;
import io.shortly.redirect.model.LinkChange;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** The change feed of the {@code link_snapshot} view, read from the primary. */
@Repository
public class LinkChangeRepository {

    private final JdbcClient primary;

    public LinkChangeRepository(
            @Qualifier(PrimaryDatabaseConfig.PRIMARY_DATA_SOURCE) DataSource primaryDataSource) {
        this.primary = JdbcClient.create(primaryDataSource);
    }

    /** The next {@code limit} changes after {@code fromSeq}, oldest first. */
    public List<LinkChange> changesAfter(long fromSeq, int limit) {
        return primary.sql(
                        """
                        SELECT code, target_url, status, version, change_seq
                        FROM link_snapshot
                        WHERE change_seq > ?
                        ORDER BY change_seq
                        LIMIT ?
                        """)
                .param(fromSeq)
                .param(limit)
                .query((row, _) -> change(row))
                .list();
    }

    /** The changes up to {@code upToSeq} whose {@code updated_at} is after {@code since}. */
    public List<LinkChange> changedSince(Instant since, long upToSeq) {
        return primary.sql(
                        """
                        SELECT code, target_url, status, version, change_seq
                        FROM link_snapshot
                        WHERE change_seq <= ? AND updated_at > ?
                        ORDER BY change_seq
                        """)
                .param(upToSeq)
                .param(OffsetDateTime.ofInstant(since, ZoneOffset.UTC))
                .query((row, _) -> change(row))
                .list();
    }

    public long maxSeq() {
        return primary.sql("SELECT coalesce(max(change_seq), 0) FROM link_snapshot")
                .query((row, _) -> row.getLong(1))
                .single();
    }

    private static LinkChange change(ResultSet row) throws SQLException {
        return new LinkChange(row.getLong("change_seq"), LinkRepository.snapshot(row));
    }
}
