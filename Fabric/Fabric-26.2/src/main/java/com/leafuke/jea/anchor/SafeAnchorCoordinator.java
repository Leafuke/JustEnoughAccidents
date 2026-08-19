package com.leafuke.jea.anchor;

import com.leafuke.jea.JustEnoughAccidents;
import com.leafuke.jea.config.JeaConfig;
import com.leafuke.jea.incident.CreateNowBackupStrategy;
import com.leafuke.jea.incident.DangerScanState;
import com.leafuke.minebackup.api.v2.BackupCatalogRequest;
import com.leafuke.minebackup.api.v2.BackupCatalogResult;
import com.leafuke.minebackup.api.v2.BackupRequest;
import com.leafuke.minebackup.api.v2.BackupResult;
import com.leafuke.minebackup.api.v2.MineBackupApi;
import com.leafuke.minebackup.api.v2.OperationHandle;
import com.leafuke.minebackup.api.v2.OperationPhase;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;

public final class SafeAnchorCoordinator implements AutoCloseable {
    private static final String CALLER_ID = JustEnoughAccidents.MOD_ID + ".safe_anchor";
    private static final Duration RETRY_BACKOFF = Duration.ofSeconds(10);

    private final JeaConfig.SafeAnchor config;
    private final JeaConfig.Backup backupConfig;
    private final Clock clock;
    private final Executor serverExecutor;
    private final Operations operations;
    private Instant quietSince;
    private Instant lastAnchorRefreshSatisfiedAt;
    private Instant retryAfter;
    private boolean bootstrapComplete;
    private boolean maintenanceInFlight;
    private boolean closed;

    public SafeAnchorCoordinator(
            JeaConfig.SafeAnchor config,
            JeaConfig.Backup backupConfig,
            Executor serverExecutor) {
        this(config, backupConfig, Clock.systemUTC(), serverExecutor, new ApiOperations());
    }

    SafeAnchorCoordinator(
            JeaConfig.SafeAnchor config,
            JeaConfig.Backup backupConfig,
            Clock clock,
            Executor serverExecutor,
            Operations operations) {
        this.config = Objects.requireNonNull(config, "config");
        this.backupConfig = Objects.requireNonNull(backupConfig, "backupConfig");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.serverExecutor = Objects.requireNonNull(serverExecutor, "serverExecutor");
        this.operations = Objects.requireNonNull(operations, "operations");
    }

    public void onIncidentSignal() {
        quietSince = null;
    }

    public void tick(DangerScanState scanState, boolean incidentPending, boolean incidentInFlight) {
        if (closed || !config.enabled) {
            return;
        }
        Objects.requireNonNull(scanState, "scanState");

        Instant now = clock.instant();
        if (!scanState.isQuiet()) {
            quietSince = null;
            return;
        }
        if (quietSince == null) {
            quietSince = now;
        }
        if (incidentPending || incidentInFlight || maintenanceInFlight || isBackoffActive(now)) {
            return;
        }
        if (!bootstrapComplete) {
            bootstrap();
            return;
        }
        if (!quietElapsed(now) || !refreshDue(now)) {
            return;
        }
        createAnchor();
    }

    public boolean maintenanceInFlight() {
        return maintenanceInFlight;
    }

    private void bootstrap() {
        maintenanceInFlight = true;
        final CompletionStage<BackupCatalogResult> stage;
        try {
            stage = operations.listBackups();
        } catch (RuntimeException ex) {
            finishBootstrap(null, ex);
            return;
        }
        stage.whenComplete((result, throwable) -> executeOnServer(() -> finishBootstrap(result, throwable)));
    }

    private void finishBootstrap(BackupCatalogResult result, Throwable throwable) {
        if (closed) {
            return;
        }
        maintenanceInFlight = false;
        if (throwable != null || result == null || result.outcome() != BackupCatalogResult.Outcome.SUCCESS) {
            scheduleRetry("bootstrap catalog", result == null ? "exception" : result.outcome().name(), throwable);
            return;
        }

        lastAnchorRefreshSatisfiedAt = SafeAnchorSelector.latestBefore(result.entries(), Instant.MAX)
                .flatMap(entry -> entry.createdAt())
                .orElse(null);
        bootstrapComplete = true;
        retryAfter = null;
        JustEnoughAccidents.LOGGER.info("JEA safe-anchor bootstrap catalog completed.");
    }

    private void createAnchor() {
        maintenanceInFlight = true;
        final OperationHandle<BackupResult> handle;
        try {
            handle = operations.createBackup(CreateNowBackupStrategy.createRequest(
                    CALLER_ID, backupConfig, SafeAnchorProtocol.COMMENT));
        } catch (RuntimeException ex) {
            finishAnchor(null, ex);
            return;
        }
        if (handle.phase() == OperationPhase.REJECTED) {
            scheduleRetry("safe-anchor backup", "REJECTED", null);
            return;
        }
        handle.completion().whenComplete((result, throwable) ->
                executeOnServer(() -> finishAnchor(result, throwable)));
    }

    private void finishAnchor(BackupResult result, Throwable throwable) {
        if (closed) {
            return;
        }
        maintenanceInFlight = false;
        if (throwable != null || result == null) {
            scheduleRetry("safe-anchor backup", "exception", throwable);
            return;
        }
        if (result.outcome() == BackupResult.Outcome.CREATED
                || result.outcome() == BackupResult.Outcome.NO_CHANGES) {
            lastAnchorRefreshSatisfiedAt = clock.instant();
            retryAfter = null;
            JustEnoughAccidents.LOGGER.info(
                    "JEA safe anchor refresh satisfied: outcome={}", result.outcome());
            return;
        }
        String failure = result.failure()
                .map(value -> value.code() + ": " + value.message())
                .orElse(result.outcome().name());
        scheduleRetry("safe-anchor backup", failure, null);
    }

    private boolean quietElapsed(Instant now) {
        return !now.isBefore(quietSince.plusSeconds(config.quietSeconds));
    }

    private boolean refreshDue(Instant now) {
        return lastAnchorRefreshSatisfiedAt == null
                || !now.isBefore(lastAnchorRefreshSatisfiedAt.plus(Duration.ofMinutes(config.refreshMinutes)));
    }

    private boolean isBackoffActive(Instant now) {
        return retryAfter != null && now.isBefore(retryAfter);
    }

    private void scheduleRetry(String operation, String detail, Throwable throwable) {
        maintenanceInFlight = false;
        retryAfter = clock.instant().plus(RETRY_BACKOFF);
        if (throwable == null) {
            JustEnoughAccidents.LOGGER.warn(
                    "JEA {} failed ({}); retrying no earlier than {}.", operation, detail, retryAfter);
        } else {
            JustEnoughAccidents.LOGGER.warn(
                    "JEA {} failed ({}); retrying no earlier than {}.", operation, detail, retryAfter, throwable);
        }
    }

    private void executeOnServer(Runnable task) {
        try {
            serverExecutor.execute(task);
        } catch (RuntimeException ex) {
            JustEnoughAccidents.LOGGER.warn("Could not deliver JEA safe-anchor completion on the server thread", ex);
        }
    }

    @Override
    public void close() {
        closed = true;
        maintenanceInFlight = false;
        quietSince = null;
    }

    interface Operations {
        CompletionStage<BackupCatalogResult> listBackups();

        OperationHandle<BackupResult> createBackup(BackupRequest request);
    }

    private static final class ApiOperations implements Operations {
        @Override
        public CompletionStage<BackupCatalogResult> listBackups() {
            return MineBackupApi.getInstance().listCurrentBackups(BackupCatalogRequest.create(CALLER_ID));
        }

        @Override
        public OperationHandle<BackupResult> createBackup(BackupRequest request) {
            return MineBackupApi.getInstance().backupCurrent(request);
        }
    }
}
