package com.leafuke.jea.incident;

import com.leafuke.jea.JustEnoughAccidents;
import com.leafuke.minebackup.api.v2.BackupResult;
import com.leafuke.minebackup.api.v2.OperationPhase;
import com.leafuke.minebackup.api.v2.OperationHandle;
import net.minecraft.server.MinecraftServer;

import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public final class IncidentCoordinator implements AutoCloseable {
    private final MinecraftServer server;
    private final IncidentBackupStrategy strategy;
    private final IncidentFeedback feedback;
    private final long cooldownNanos;
    private final EnumMap<IncidentType, LinkedHashMap<Object, IncidentSignal>> pending =
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
        this.server = server;
        this.strategy = strategy;
        this.feedback = feedback;
        this.cooldownNanos = TimeUnit.SECONDS.toNanos(cooldownSeconds);
    }

    public void signal(IncidentSignal signal) {
        if (closed) {
            return;
        }
        Object key = signal.playerId() == null ? IncidentType.SCOREBOARD : signal.playerId();
        pending.computeIfAbsent(signal.type(), ignored -> new LinkedHashMap<>())
                .putIfAbsent(key, signal);
    }

    public void flush() {
        if (closed || pending.isEmpty()) {
            return;
        }

        var signals = new ArrayList<IncidentSignal>();
        for (var type : IncidentType.values()) {
            var byActor = pending.get(type);
            if (byActor != null) {
                signals.addAll(byActor.values());
            }
        }
        pending.clear();
        var batch = new IncidentBatch(signals);

        if (inFlight) {
            JustEnoughAccidents.LOGGER.warn(
                    "Suppressed JEA incident snapshot while another request is in flight: {}",
                    batch.comment());
            feedback.suppressedInFlight(batch);
            return;
        }

        long now = System.nanoTime();
        if (now < cooldownUntilNanos) {
            long remainingMillis = Duration.ofNanos(cooldownUntilNanos - now).toMillis();
            JustEnoughAccidents.LOGGER.info(
                    "Suppressed JEA incident snapshot during cooldown ({} ms remaining): {}",
                    remainingMillis,
                    batch.comment());
            feedback.suppressedCooldown(batch, remainingMillis);
            return;
        }

        long startedAt = System.nanoTime();
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
                server.execute(() -> finish(
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
        long elapsedMillis = Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
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
    }
}
