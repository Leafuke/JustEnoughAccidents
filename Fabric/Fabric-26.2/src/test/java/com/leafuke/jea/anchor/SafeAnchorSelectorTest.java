package com.leafuke.jea.anchor;

import com.leafuke.minebackup.api.v2.BackupEntry;
import com.leafuke.minebackup.api.v2.BackupId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SafeAnchorSelectorTest {
    private static final Instant INCIDENT_AT = Instant.parse("2026-08-19T10:45:00Z");

    @Test
    void selectsLatestExactSafeAnchorBeforeIncidentRegardlessOfCatalogOrder() {
        var expected = entry("safe-1030", "2026-08-19T10:30:00Z", Optional.of(SafeAnchorProtocol.COMMENT));
        var result = SafeAnchorSelector.latestBefore(List.of(
                entry("safe-1050", "2026-08-19T10:50:00Z", Optional.of(SafeAnchorProtocol.COMMENT)),
                expected,
                entry("safe-1000", "2026-08-19T10:00:00Z", Optional.of(SafeAnchorProtocol.COMMENT))), INCIDENT_AT);

        assertEquals(expected, result.orElseThrow());
    }

    @Test
    void acceptsAnchorCreatedInTheSameInstantAsIncident() {
        var anchor = entry("safe-same", "2026-08-19T10:45:00Z", Optional.of(SafeAnchorProtocol.COMMENT));

        assertEquals(anchor, SafeAnchorSelector.latestBefore(List.of(anchor), INCIDENT_AT).orElseThrow());
    }

    @Test
    void rejectsNearMatchesIncidentSnapshotsAndMetadataMissingEntries() {
        var legacy = new BackupEntry(
                BackupId.of("legacy"), Optional.empty(), OptionalLong.empty(), Optional.of(SafeAnchorProtocol.COMMENT));
        var result = SafeAnchorSelector.latestBefore(List.of(
                entry("different", "2026-08-19T10:30:00Z", Optional.of("[JEA SAFE] extra")),
                entry("embedded", "2026-08-19T10:30:00Z", Optional.of("foo [JEA SAFE]")),
                entry("incident", "2026-08-19T10:30:00Z", Optional.of("[JEA] Alex: lava")),
                entry("missing-comment", "2026-08-19T10:30:00Z", Optional.empty()),
                legacy), INCIDENT_AT);

        assertTrue(result.isEmpty());
    }

    @Test
    void usesBackupIdAsDeterministicTieBreakForIdenticalCreatedAt() {
        var result = SafeAnchorSelector.latestBefore(List.of(
                entry("anchor-a", "2026-08-19T10:30:00Z", Optional.of(SafeAnchorProtocol.COMMENT)),
                entry("anchor-z", "2026-08-19T10:30:00Z", Optional.of(SafeAnchorProtocol.COMMENT))), INCIDENT_AT);

        assertEquals("anchor-z", result.orElseThrow().backupId().value());
    }

    private static BackupEntry entry(String id, String createdAt, Optional<String> comment) {
        return new BackupEntry(
                BackupId.of(id), Optional.of(Instant.parse(createdAt)), OptionalLong.empty(), comment);
    }
}
