package com.leafuke.jea.runtime;

import com.leafuke.jea.JustEnoughAccidents;
import com.leafuke.jea.config.JeaConfig;
import com.leafuke.jea.incident.CreateNowBackupStrategy;
import com.leafuke.jea.incident.IncidentCoordinator;
import com.leafuke.jea.incident.IncidentSignal;
import com.leafuke.jea.incident.IncidentType;
import com.leafuke.jea.incident.PlayerDangerScanner;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public final class JeaSession implements AutoCloseable {
    private final MinecraftServer server;
    private final JeaConfig config;
    private final PlayerDangerScanner scanner;
    private final IncidentCoordinator coordinator;

    public JeaSession(MinecraftServer server, JeaConfig config) {
        this.server = server;
        this.config = config;
        this.scanner = new PlayerDangerScanner(config.detectors);
        this.coordinator = new IncidentCoordinator(
                server,
                new CreateNowBackupStrategy(config.backup),
                config.cooldownSeconds);
    }

    public void start() {
        JustEnoughAccidents.LOGGER.info(
                "JEA session enabled for '{}': mode={}, compression={} level={}",
                server.getWorldData().getLevelName(),
                config.backup.mode,
                config.backup.compressionMethod,
                config.backup.compressionLevel);
    }

    public void tick() {
        scanner.scan(server, coordinator::signal);
        coordinator.flush();
    }

    public void signalTotem(ServerPlayer player) {
        if (config.detectors.totem.enabled && scanner.isEligible(player)) {
            coordinator.signal(IncidentSignal.player(IncidentType.TOTEM, player));
        }
    }

    @Override
    public void close() {
        scanner.clear();
        coordinator.close();
    }
}
