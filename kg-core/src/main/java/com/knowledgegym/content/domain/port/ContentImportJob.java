package com.knowledgegym.content.domain.port;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Port chạy import docs/ nền (HTTP 202). Presentation chỉ phụ thuộc port này —
 * không inject adapter Spring trong {@code infrastructure.content}.
 */
public interface ContentImportJob {

    enum Status { RUNNING, SUCCEEDED, FAILED }

    /** Thống kê sau import thành công; {@code null} khi RUNNING/FAILED. */
    record ImportStats(int topics, int modules, int questions, int deleted,
                       List<String> orphanModuleSlugs) {}

    record JobSnapshot(UUID id, Status status, Instant startedAt, Instant finishedAt,
                       ImportStats result, String errorMessage) {}

    /** @return snapshot RUNNING ngay sau khi xếp hàng — controller trả 202 + id. */
    JobSnapshot submit();

    Optional<JobSnapshot> find(UUID jobId);
}
