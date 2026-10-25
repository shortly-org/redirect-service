package io.shortly.redirect.repository;

import static org.assertj.core.api.Assertions.assertThat;

import io.shortly.redirect.AbstractIntegrationTest;
import io.shortly.redirect.ContractDatabase;
import io.shortly.redirect.model.LinkSnapshot;
import io.shortly.redirect.model.LinkState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class LinkRepositoryTest extends AbstractIntegrationTest {

    @Autowired private LinkRepository repository;
    @Autowired private ContractDatabase db;

    @BeforeEach
    void emptyContractTables() {
        db.truncate();
    }

    @Test
    void findsSnapshotThroughView() {
        db.upsertLink("aB3x9kQ2z", "https://example.org/x", "INACTIVE", 4);

        assertThat(repository.findByCode("aB3x9kQ2z"))
                .contains(
                        new LinkSnapshot(
                                "aB3x9kQ2z", "https://example.org/x", LinkState.INACTIVE, 4));
        assertThat(repository.findByCode("000000000")).isEmpty();
    }
}
