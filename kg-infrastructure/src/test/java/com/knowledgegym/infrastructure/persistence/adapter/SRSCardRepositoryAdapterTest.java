package com.knowledgegym.infrastructure.persistence.adapter;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * `toPgUuidArrayLiteral` là boundary sát native query: chuỗi này được nhúng thẳng vào câu
 * `INSERT ... unnest(cast(:questionIds as uuid[]))`. UUID chỉ chứa `[0-9a-f-]` nên an toàn, nhưng
 * lớp lọc ký tự lạ là thứ dễ bị gỡ nhầm khi refactor — test này cố định nó.
 */
class SRSCardRepositoryAdapterTest {

    @Test
    void buildsPgArrayLiteralPreservingOrder() {
        UUID first = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID second = UUID.fromString("22222222-2222-2222-2222-222222222222");

        assertThat(SRSCardRepositoryAdapter.toPgUuidArrayLiteral(List.of(first, second)))
                .isEqualTo("{11111111-1111-1111-1111-111111111111,"
                        + "22222222-2222-2222-2222-222222222222}");
    }

    @Test
    void emptyCollectionProducesEmptyPgArray() {
        assertThat(SRSCardRepositoryAdapter.toPgUuidArrayLiteral(List.of())).isEqualTo("{}");
    }
}
