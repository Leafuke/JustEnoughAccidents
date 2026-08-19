package com.leafuke.jea.runtime;

import com.leafuke.jea.JustEnoughAccidents;
import com.leafuke.jea.anchor.SafeAnchorCoordinator;
import com.leafuke.jea.config.JeaConfig;
import com.leafuke.jea.incident.CreateNowBackupStrategy;
import com.leafuke.jea.incident.IncidentCoordinator;
import com.leafuke.jea.incident.IncidentNotifier;
import com.leafuke.jea.incident.IncidentSignal;
import com.leafuke.jea.incident.IncidentType;
import com.leafuke.jea.incident.PlayerDangerScanner;
import com.leafuke.jea.incident.ScoreboardTrigger;
import com.leafuke.minebackup.api.v2.MineBackupApi;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public final class JeaSession implements AutoCloseable {
    private final MinecraftServer server;
    private final JeaConfig config;
    private final PlayerDangerScanner scanner;
    private final ScoreboardTrigger scoreboardTrigger;
    private final SafeAnchorCoordinator safeAnchorCoordinator;
    private final IncidentCoordinator coordinator;

    public JeaSession(MinecraftServer server, JeaConfig config) {
        this.server = server;
        this.config = config;
        this.scanner = new PlayerDangerScanner(config.detectors);
        this.scoreboardTrigger = new ScoreboardTrigger();
        this.safeAnchorCoordinator = new SafeAnchorCoordinator(config.safeAnchor, config.backup, server);
        this.coordinator = new IncidentCoordinator(
                server,
                new CreateNowBackupStrategy(config.backup),
                new IncidentNotifier(server, safeAnchorCoordinator),
                config.cooldownSeconds,
                safeAnchorCoordinator::maintenanceInFlight);
    }

    public void start() {
        String sessionMode = server.isDedicatedServer() ? "dedicated" : "integrated";
        JustEnoughAccidents.LOGGER.info(
                "JEA session enabled for '{}': mode={}, compression={} level={}",
                server.getWorldData().getLevelName(),
                sessionMode,
                config.backup.compressionMethod,
                config.backup.compressionLevel);
        if (server.isDedicatedServer()) {
            var status = MineBackupApi.getInstance().runtimeStatus();
            if (!status.dedicatedRestoreAvailable()) {
                JustEnoughAccidents.LOGGER.warn(
                        "JEA dedicated detection is enabled, but dedicated restore is unavailable: {}",
                        status.dedicatedRestoreUnavailableReason().orElse("unknown"));
            }
        }
    }

    public void tick() {
        if (config.scoreboard.enabled && scoreboardTrigger.consume(server)) {
            signal(IncidentSignal.global(IncidentType.SCOREBOARD));
        }
        var scanState = scanner.scan(server, this::signal);
        coordinator.flush();
        safeAnchorCoordinator.tick(scanState, coordinator.hasPending(), coordinator.isInFlight());
    }

    public void signalTotem(ServerPlayer player) {
        if (config.detectors.totem.enabled && scanner.isEligible(player)) {
            signal(IncidentSignal.player(IncidentType.TOTEM, player));
        }
    }

    private void signal(IncidentSignal signal) {
        safeAnchorCoordinator.onIncidentSignal();
        coordinator.signal(signal);
    }

    @Override
    public void close() {
        scanner.clear();
        coordinator.close();
        safeAnchorCoordinator.close();
    }
}
