package io.shortly.redirect.repository;

import static org.assertj.core.api.Assertions.assertThat;

import io.shortly.redirect.AbstractIntegrationTest;
import io.shortly.redirect.ContractDatabase;
import io.shortly.redirect.model.LinkChange;
import io.shortly.redirect.model.LinkState;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class LinkChangeRepositoryTest extends AbstractIntegrationTest {

    @Autowired private LinkChangeRepository repository;
    @Autowired private ContractDatabase db;

    @BeforeEach
    void emptyContractTables() {
        db.truncate();
    }

    @Test
    void returnsRowsAfterSeqInOrderWithLimit() {
        for (int i = 1; i <= 5; i++) {
            db.upsertLink(
                    "aB3x9kQ0" + i, "https://example.org/" + i, i == 5 ? "INACTIVE" : "ACTIVE", i);
        }

        List<LinkChange> all = repository.changesAfter(0, 10);
        List<LinkChange> rows = repository.changesAfter(all.get(1).changeSeq(), 2);

        assertThat(rows).containsExactly(all.get(2), all.get(3));
        assertThat(rows.getFirst().snapshot().code()).isEqualTo("aB3x9kQ03");
        assertThat(rows.getFirst().snapshot().version()).isEqualTo(3);
        assertThat(rows.getFirst().snapshot().targetUrl()).isEqualTo("https://example.org/3");
        assertThat(repository.changesAfter(all.get(3).changeSeq(), 10))
                .extracting(c -> c.snapshot().state())
                .containsExactly(LinkState.INACTIVE);
        assertThat(repository.changesAfter(all.get(4).changeSeq(), 10)).isEmpty();
    }
}
