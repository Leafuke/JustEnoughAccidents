package com.leafuke.jea.anchor;

import com.leafuke.minebackup.api.v2.BackupEntry;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class SafeAnchorSelector {
    private static final Comparator<BackupEntry> BY_CREATED_AT_THEN_ID = Comparator
            .comparing((BackupEntry entry) -> entry.createdAt().orElseThrow())
            .thenComparing(entry -> entry.backupId().value());

    private SafeAnchorSelector() {
    }

    public static Optional<BackupEntry> latestBefore(List<BackupEntry> entries, Instant incidentAt) {
        Objects.requireNonNull(entries, "entries");
        Objects.requireNonNull(incidentAt, "incidentAt");

        return entries.stream()
                .filter(entry -> entry.comment().filter(SafeAnchorProtocol.COMMENT::equals).isPresent())
                .filter(entry -> entry.createdAt().isPresent())
                .filter(entry -> !entry.createdAt().orElseThrow().isAfter(incidentAt))
                .max(BY_CREATED_AT_THEN_ID);
    }
}
