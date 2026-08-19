package com.leafuke.jea.incident;

import com.leafuke.jea.JustEnoughAccidents;
import com.leafuke.minebackup.api.v2.BackupResult;
import com.leafuke.minebackup.api.v2.OperationPhase;
import com.leafuke.minebackup.api.v2.OperationHandle;
import net.minecraft.server.MinecraftServer;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;

public final class IncidentCoordinator implements AutoCloseable {
    private final IncidentBackupStrategy strategy;
    private final IncidentFeedback feedback;
    private final Clock clock;
    private final LongSupplier nanoTime;
    private final Executor serverExecutor;
    private final BooleanSupplier maintenanceInFlight;
    private final long cooldownNanos;
    private final EnumMap<IncidentType, LinkedHashMap<Object, TimedIncidentSignal>> pending =
            new EnumMap<>(IncidentType.class);
    private boolean inFlight;
    private boolean closed;
    private long cooldownUntilNanos;
    private UUID activeOperationId;

    public IncidentCoordinator(
            MinecraftServer server,
            IncidentBackupStrategy strategy,
            IncidentFeedback feedback,
            int cooldownSeconds) {
        this(strategy, feedback, cooldownSeconds, Clock.systemUTC(), System::nanoTime, server,
                () -> false);
    }

    IncidentCoordinator(
            IncidentBackupStrategy strategy,
            IncidentFeedback feedback,
            int cooldownSeconds,
            Clock clock,
            LongSupplier nanoTime,
            Executor serverExecutor,
            BooleanSupplier maintenanceInFlight) {
        this.strategy = strategy;
        this.feedback = feedback;
        this.clock = clock;
        this.nanoTime = nanoTime;
        this.serverExecutor = serverExecutor;
        this.maintenanceInFlight = maintenanceInFlight;
        this.cooldownNanos = TimeUnit.SECONDS.toNanos(cooldownSeconds);
    }

    public void signal(IncidentSignal signal) {
        if (closed) {
            return;
        }
        Object key = signal.playerId() == null ? IncidentType.SCOREBOARD : signal.playerId();
        pending.computeIfAbsent(signal.type(), ignored -> new LinkedHashMap<>())
                .putIfAbsent(key, new TimedIncidentSignal(signal, clock.instant()));
    }

    public void flush() {
        if (closed || pending.isEmpty()) {
            return;
        }

        if (maintenanceInFlight.getAsBoolean()) {
            JustEnoughAccidents.LOGGER.info("Deferred JEA incident snapshot because JEA maintenance is active.");
            return;
        }

        var signals = new ArrayList<IncidentSignal>();
        Instant detectedAt = null;
        for (var type : IncidentType.values()) {
            var byActor = pending.get(type);
            if (byActor != null) {
                for (var timedSignal : byActor.values()) {
                    signals.add(timedSignal.signal());
                    if (detectedAt == null || timedSignal.detectedAt().isBefore(detectedAt)) {
                        detectedAt = timedSignal.detectedAt();
                    }
                }
            }
        }
        pending.clear();
        var batch = new IncidentBatch(signals, Objects.requireNonNull(detectedAt));

        if (inFlight) {
            JustEnoughAccidents.LOGGER.warn(
                    "Suppressed JEA incident snapshot while another request is in flight: {}",
                    batch.comment());
            feedback.suppressedInFlight(batch);
            return;
        }

        long now = nanoTime.getAsLong();
        if (now < cooldownUntilNanos) {
            long remainingMillis = Duration.ofNanos(cooldownUntilNanos - now).toMillis();
            JustEnoughAccidents.LOGGER.info(
                    "Suppressed JEA incident snapshot during cooldown ({} ms remaining): {}",
                    remainingMillis,
                    batch.comment());
            feedback.suppressedCooldown(batch, remainingMillis);
            return;
        }

        long startedAt = nanoTime.getAsLong();
        final OperationHandle<BackupResult> handle;
        try {
            handle = strategy.submit(batch);
        } catch (RuntimeException ex) {
            JustEnoughAccidents.LOGGER.error(
                    "Could not submit JEA incident snapshot: {}",
                    batch.comment(),
                    ex);
            feedback.submissionFailed(batch, ex);
            return;
        }

        if (handle.phase() != OperationPhase.REJECTED) {
            inFlight = true;
            activeOperationId = handle.id();
            cooldownUntilNanos = now + cooldownNanos;
            JustEnoughAccidents.LOGGER.info(
                    "MineBackup accepted JEA incident snapshot {}: {}",
                    handle.id(),
                    batch.comment());
            feedback.accepted(batch);
        } else {
            JustEnoughAccidents.LOGGER.warn(
                    "MineBackup immediately rejected JEA incident snapshot {}: {}",
                    handle.id(),
                    batch.comment());
        }

        handle.completion().whenComplete((result, throwable) -> {
            try {
                serverExecutor.execute(() -> finish(
                        handle.id(),
                        batch,
                        result,
                        throwable,
                        startedAt));
            } catch (RuntimeException ex) {
                JustEnoughAccidents.LOGGER.warn(
                        "Could not deliver JEA backup completion on the server thread", ex);
            }
        });
    }

    private void finish(
            UUID operationId,
            IncidentBatch batch,
            BackupResult result,
            Throwable throwable,
            long startedAt) {
        if (Objects.equals(activeOperationId, operationId)) {
            inFlight = false;
            activeOperationId = null;
        }
        if (closed) {
            return;
        }
        long elapsedMillis = Duration.ofNanos(nanoTime.getAsLong() - startedAt).toMillis();
        feedback.completed(batch, result, throwable, elapsedMillis);
        if (throwable != null) {
            JustEnoughAccidents.LOGGER.error(
                    "JEA incident snapshot completed exceptionally after {} ms: {}",
                    elapsedMillis,
                    batch.comment(),
                    throwable);
            return;
        }

        String failure = result.failure()
                .map(value -> value.code() + ": " + value.message())
                .orElse("");
        if (result.outcome() == BackupResult.Outcome.CREATED
                || result.outcome() == BackupResult.Outcome.NO_CHANGES) {
            JustEnoughAccidents.LOGGER.info(
                    "JEA incident snapshot finished after {} ms: outcome={}, file={}, incidents={}",
                    elapsedMillis,
                    result.outcome(),
                    result.backupId().map(value -> value.value()).orElse(""),
                    batch.comment());
        } else {
            JustEnoughAccidents.LOGGER.warn(
                    "JEA incident snapshot finished after {} ms: outcome={}, failure={}, incidents={}",
                    elapsedMillis,
                    result.outcome(),
                    failure,
                    batch.comment());
        }
    }

    @Override
    public void close() {
        closed = true;
        pending.clear();
        inFlight = false;
        activeOperationId = null;
    }

    public boolean hasPending() {
        return !pending.isEmpty();
    }

    public boolean isInFlight() {
        return inFlight;
    }

    private record TimedIncidentSignal(IncidentSignal signal, Instant detectedAt) {
    }
}
