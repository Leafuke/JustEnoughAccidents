package com.leafuke.jea.incident;

import com.leafuke.minebackup.api.v2.BackupResult;
import com.leafuke.minebackup.api.v2.OperationHandle;
import com.leafuke.minebackup.api.v2.OperationPhase;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IncidentCoordinatorTest {
    @Test
    void maintenanceDefersAndPreservesPendingIncidentUntilItFinishes() {
        var maintenanceInFlight = new AtomicBoolean(true);
        var strategy = new RecordingStrategy(OperationPhase.RUNNING);
        var clock = Clock.fixed(Instant.parse("2026-08-19T04:00:00Z"), ZoneOffset.UTC);
        var coordinator = coordinator(strategy, clock, maintenanceInFlight, 0);

        coordinator.signal(new IncidentSignal(IncidentType.LAVA, UUID.randomUUID(), "Alex"));
        coordinator.flush();

        assertEquals(0, strategy.submissions);
        assertTrue(coordinator.hasPending());

        maintenanceInFlight.set(false);
        coordinator.flush();

        assertEquals(1, strategy.submissions);
        assertFalse(coordinator.hasPending());
    }

    @Test
    void deferredSignalsCoalesceAndPreserveTheEarliestDetectionTime() {
        var maintenanceInFlight = new AtomicBoolean(true);
        var strategy = new RecordingStrategy(OperationPhase.RUNNING);
        var mutableClock = new MutableClock(Instant.parse("2026-08-19T04:00:00Z"));
        var coordinator = coordinator(strategy, mutableClock, maintenanceInFlight, 0);
        UUID playerId = UUID.randomUUID();

        coordinator.signal(new IncidentSignal(IncidentType.LAVA, playerId, "Alex"));
        mutableClock.advanceSeconds(10);
        coordinator.signal(new IncidentSignal(IncidentType.LOW_HEALTH, playerId, "Alex"));
        coordinator.flush();
        maintenanceInFlight.set(false);
        coordinator.flush();

        assertEquals(1, strategy.submissions);
        assertEquals(Instant.parse("2026-08-19T04:00:00Z"), strategy.lastBatch.detectedAt());
        assertEquals(2, strategy.lastBatch.signals().size());
    }

    @Test
    void immediateExternalRejectionIsNotRetried() {
        var strategy = new RecordingStrategy(OperationPhase.REJECTED);
        var coordinator = coordinator(strategy, Clock.systemUTC(), new AtomicBoolean(false), 0);

        coordinator.signal(IncidentSignal.global(IncidentType.SCOREBOARD));
        coordinator.flush();
        coordinator.flush();

        assertEquals(1, strategy.submissions);
        assertFalse(coordinator.hasPending());
    }

    @Test
    void acceptedSubmissionStartsCooldown() {
        var time = new AtomicLong(0L);
        var strategy = new RecordingStrategy(OperationPhase.RUNNING);
        var coordinator = new IncidentCoordinator(
                strategy,
                new NoOpFeedback(),
                60,
                Clock.systemUTC(),
                time::get,
                Runnable::run,
                () -> false);

        coordinator.signal(IncidentSignal.global(IncidentType.SCOREBOARD));
        coordinator.flush();
        coordinator.signal(IncidentSignal.global(IncidentType.SCOREBOARD));
        coordinator.flush();

        assertEquals(1, strategy.submissions);
    }

    private static IncidentCoordinator coordinator(
            RecordingStrategy strategy,
            Clock clock,
            AtomicBoolean maintenanceInFlight,
            int cooldownSeconds) {
        return new IncidentCoordinator(
                strategy,
                new NoOpFeedback(),
                cooldownSeconds,
                clock,
                () -> 0L,
                Runnable::run,
                maintenanceInFlight::get);
    }

    private static final class RecordingStrategy implements IncidentBackupStrategy {
        private final OperationPhase phase;
        private int submissions;
        private IncidentBatch lastBatch;

        private RecordingStrategy(OperationPhase phase) {
            this.phase = phase;
        }

        @Override
        public OperationHandle<BackupResult> submit(IncidentBatch batch) {
            submissions++;
            lastBatch = batch;
            return new TestOperationHandle(UUID.randomUUID(), phase);
        }
    }

    private record TestOperationHandle(UUID id, OperationPhase phase) implements OperationHandle<BackupResult> {
        @Override
        public String callerId() {
            return "jea.incident";
        }

        @Override
        public CompletionStage<BackupResult> completion() {
            return new CompletableFuture<>();
        }
    }

    private static final class NoOpFeedback implements IncidentFeedback {
        @Override public void suppressedInFlight(IncidentBatch batch) { }
        @Override public void suppressedCooldown(IncidentBatch batch, long remainingMillis) { }
        @Override public void submissionFailed(IncidentBatch batch, Throwable throwable) { }
        @Override public void accepted(IncidentBatch batch) { }
        @Override public void completed(IncidentBatch batch, BackupResult result, Throwable throwable, long elapsedMillis) { }
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        void advanceSeconds(long seconds) {
            instant = instant.plusSeconds(seconds);
        }

        @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return instant; }
    }
}
