package com.knowledgegym.shared.domain.port;

/** Runs the host backup script (scripts/backup-db.sh) as a single code path. */
public interface BackupPort {
    Result run();

    record Result(boolean success, int exitCode, String output) {}
}
