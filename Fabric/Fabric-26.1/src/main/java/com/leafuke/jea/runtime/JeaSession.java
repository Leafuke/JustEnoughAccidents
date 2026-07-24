package com.leafuke.jea.runtime;

import com.leafuke.jea.JustEnoughAccidents;
import com.leafuke.jea.config.JeaConfig;
import net.minecraft.server.MinecraftServer;

public final class JeaSession implements AutoCloseable {
    private final MinecraftServer server;
    private final JeaConfig config;

    public JeaSession(MinecraftServer server, JeaConfig config) {
        this.server = server;
        this.config = config;
    }

    public void start() {
        JustEnoughAccidents.LOGGER.info(
                "JEA session enabled for '{}': mode={}, compression={} level={}",
                server.getWorldData().getLevelName(),
                config.backup.mode,
                config.backup.compressionMethod,
                config.backup.compressionLevel);
    }

    @Override
    public void close() {
    }
}
