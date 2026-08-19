package com.leafuke.jea.anchor;

import com.leafuke.jea.config.JeaConfig;
import com.leafuke.jea.incident.DangerScanState;
import com.leafuke.minebackup.api.v2.BackupCatalogResult;
import com.leafuke.minebackup.api.v2.BackupEntry;
import com.leafuke.minebackup.api.v2.BackupId;
import com.leafuke.minebackup.api.v2.BackupRequest;
import com.leafuke.minebackup.api.v2.BackupResult;
import com.leafuke.minebackup.api.v2.OperationHandle;
import com.leafuke.minebackup.api.v2.OperationPhase;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SafeAnchorCoordinatorTest {
    private static final DangerScanState SAFE = new DangerScanState(1, false);

    @Test
    void doesNotScheduleMaintenanceWithoutEligiblePlayersOrWhileDangerIsActive() {
        var fixture = fixture(0, 30);

        fixture.coordinator.tick(new DangerScanState(0, false), false, false);
        fixture.coordinator.tick(new DangerScanState(1, true), false, false);

        assertEquals(0, fixture.operations.catalogRequests);
        assertEquals(0, fixture.operations.backupRequests.size());
    }

    @Test
    void waitsForQuietPeriodBeforeCreatingFirstAnchor() {
        var fixture = fixture(30, 30);
        bootstrapWithoutAnchor(fixture);

        fixture.coordinator.tick(SAFE, false, false);
        fixture.clock.advanceSeconds(29);
        fixture.coordinator.tick(SAFE, false, false);

        assertEquals(0, fixture.operations.backupRequests.size());

        fixture.clock.advanceSeconds(1);
        fixture.coordinator.tick(SAFE, false, false);

        assertEquals(1, fixture.operations.backupRequests.size());
    }

    @Test
    void honorsRefreshIntervalFromBootstrapCatalog() {
        var fixture = fixture(0, 30);
        fixture.coordinator.tick(SAFE, false, false);
        fixture.operations.completeCatalog(BackupCatalogResult.success(List.of(anchorAt(fixture.clock.instant()))));

        fixture.coordinator.tick(SAFE, false, false);
        fixture.clock.advanceSeconds(29 * 60L);
        fixture.coordinator.tick(SAFE, false, false);

        assertEquals(0, fixture.operations.backupRequests.size());

        fixture.clock.advanceSeconds(60);
        fixture.coordinator.tick(SAFE, false, false);

        assertEquals(1, fixture.operations.backupRequests.size());
    }

    @Test
    void incidentSignalRestartsQuietTimer() {
        var fixture = fixture(30, 30);
        bootstrapWithoutAnchor(fixture);
        fixture.coordinator.tick(SAFE, false, false);
        fixture.clock.advanceSeconds(29);
        fixture.coordinator.onIncidentSignal();
        fixture.coordinator.tick(SAFE, false, false);
        fixture.clock.advanceSeconds(29);
        fixture.coordinator.tick(SAFE, false, false);

        assertEquals(0, fixture.operations.backupRequests.size());

        fixture.clock.advanceSeconds(1);
        fixture.coordinator.tick(SAFE, false, false);

        assertEquals(1, fixture.operations.backupRequests.size());
    }

    @Test
    void createdAndNoChangesEachSatisfyRefreshWithoutImmediateRepeat() {
        assertRefreshSatisfied(BackupResult.Outcome.CREATED);
        assertRefreshSatisfied(BackupResult.Outcome.NO_CHANGES);
    }

    @Test
    void failedMaintenanceUsesTenSecondBackoff() {
        var fixture = fixture(0, 30);
        bootstrapWithoutAnchor(fixture);
        fixture.coordinator.tick(SAFE, false, false);
        fixture.operations.completeBackup(new BackupResult(
                BackupResult.Outcome.FAILED, Optional.empty(), Optional.empty()));

        fixture.clock.advanceSeconds(9);
        fixture.coordinator.tick(SAFE, false, false);
        assertEquals(1, fixture.operations.backupRequests.size());

        fixture.clock.advanceSeconds(1);
        fixture.coordinator.tick(SAFE, false, false);
        assertEquals(2, fixture.operations.backupRequests.size());
    }

    @Test
    void immediatelyRejectedMaintenanceUsesTenSecondBackoff() {
        var fixture = fixture(0, 30);
        bootstrapWithoutAnchor(fixture);
        fixture.operations.nextPhase = OperationPhase.REJECTED;
        fixture.coordinator.tick(SAFE, false, false);

        fixture.clock.advanceSeconds(9);
        fixture.coordinator.tick(SAFE, false, false);
        assertEquals(1, fixture.operations.backupRequests.size());

        fixture.clock.advanceSeconds(1);
        fixture.coordinator.tick(SAFE, false, false);
        assertEquals(2, fixture.operations.backupRequests.size());
    }

    @Test
    void recoveryLookupSelectsTheAnchorFromBeforeTheIncidentAndMarksMaintenanceActive() {
        var fixture = fixture(0, 30);
        bootstrapWithoutAnchor(fixture);
        fixture.operations.startNextCatalog();
        var selected = new AtomicReference<Optional<BackupEntry>>();
        Instant incidentAt = Instant.parse("2026-08-19T10:45:00Z");

        fixture.coordinator.lookupRecovery(incidentAt, selected::set, throwable -> {
            throw new AssertionError(throwable);
        });

        assertEquals(true, fixture.coordinator.maintenanceInFlight());
        fixture.operations.completeCatalog(BackupCatalogResult.success(List.of(
                anchorAt(Instant.parse("2026-08-19T10:30:00Z")),
                anchorAt(Instant.parse("2026-08-19T10:50:00Z")))));

        assertEquals(Optional.of(anchorAt(Instant.parse("2026-08-19T10:30:00Z"))), selected.get());
        assertEquals(false, fixture.coordinator.maintenanceInFlight());
    }

    private static void assertRefreshSatisfied(BackupResult.Outcome outcome) {
        var fixture = fixture(0, 30);
        bootstrapWithoutAnchor(fixture);
        fixture.coordinator.tick(SAFE, false, false);
        fixture.operations.completeBackup(new BackupResult(outcome, Optional.empty(), Optional.empty()));
        fixture.coordinator.tick(SAFE, false, false);

        assertEquals(1, fixture.operations.backupRequests.size());
    }

    private static void bootstrapWithoutAnchor(Fixture fixture) {
        fixture.coordinator.tick(SAFE, false, false);
        fixture.operations.completeCatalog(BackupCatalogResult.success(List.of()));
    }

    private static Fixture fixture(int quietSeconds, int refreshMinutes) {
        var config = new JeaConfig.SafeAnchor();
        config.quietSeconds = quietSeconds;
        config.refreshMinutes = refreshMinutes;
        var clock = new MutableClock(Instant.parse("2026-08-19T10:00:00Z"));
        var operations = new RecordingOperations();
        return new Fixture(clock, operations, new SafeAnchorCoordinator(
                config, new JeaConfig.Backup(), clock, Runnable::run, operations));
    }

    private static BackupEntry anchorAt(Instant createdAt) {
        return new BackupEntry(
                BackupId.of("safe-anchor"), Optional.of(createdAt), OptionalLong.empty(),
                Optional.of(SafeAnchorProtocol.COMMENT));
    }

    private record Fixture(
            MutableClock clock,
            RecordingOperations operations,
            SafeAnchorCoordinator coordinator) {
    }

    private static final class RecordingOperations implements SafeAnchorCoordinator.Operations {
        private int catalogRequests;
        private CompletableFuture<BackupCatalogResult> catalog = new CompletableFuture<>();
        private final List<BackupRequest> backupRequests = new ArrayList<>();
        private TestOperationHandle backupHandle;
        private OperationPhase nextPhase = OperationPhase.RUNNING;

        @Override
        public CompletionStage<BackupCatalogResult> listBackups() {
            catalogRequests++;
            return catalog;
        }

        @Override
        public OperationHandle<BackupResult> createBackup(BackupRequest request) {
            backupRequests.add(request);
            backupHandle = new TestOperationHandle(nextPhase);
            return backupHandle;
        }

        void completeCatalog(BackupCatalogResult result) {
            catalog.complete(result);
        }

        void startNextCatalog() {
            catalog = new CompletableFuture<>();
        }

        void completeBackup(BackupResult result) {
            backupHandle.completion.complete(result);
        }
    }

    private static final class TestOperationHandle implements OperationHandle<BackupResult> {
        private final UUID id = UUID.randomUUID();
        private final CompletableFuture<BackupResult> completion = new CompletableFuture<>();
        private final OperationPhase phase;

        private TestOperationHandle(OperationPhase phase) {
            this.phase = phase;
        }

        @Override public UUID id() { return id; }
        @Override public String callerId() { return "just_enough_accidents.safe_anchor"; }
        @Override public OperationPhase phase() { return phase; }
        @Override public CompletionStage<BackupResult> completion() { return completion; }
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        void advanceSeconds(long seconds) {
            instant = instant.plusSeconds(seconds);
        }

        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return instant; }
    }
}
