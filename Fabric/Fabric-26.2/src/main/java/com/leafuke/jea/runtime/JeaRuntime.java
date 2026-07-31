package com.leafuke.jea.runtime;

import com.leafuke.jea.JustEnoughAccidents;
import com.leafuke.jea.config.JeaConfigManager;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public final class JeaRuntime {
    private static JeaSession session;

    private JeaRuntime() {
    }

    public static void register() {
        ServerLifecycleEvents.SERVER_STARTING.register(JeaRuntime::start);
        ServerLifecycleEvents.SERVER_STOPPING.register(JeaRuntime::stop);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> session = null);
        ServerTickEvents.END_SERVER_TICK.register(JeaRuntime::tick);
    }

    public static void signalTotem(ServerPlayer player) {
        var current = session;
        if (current != null) {
            current.signalTotem(player);
        }
    }

    private static void start(MinecraftServer server) {
        stop(server);

        var result = JeaConfigManager.load();
        if (!result.isSuccess()) {
            JustEnoughAccidents.LOGGER.error(
                    "JEA is disabled for this server session because its configuration is invalid: {}",
                    result.error());
            return;
        }

        if (server.isDedicatedServer()) {
            JustEnoughAccidents.LOGGER.warn(
                    "JEA 0.2.0 does not support dedicated servers; all detection and backup requests are disabled.");
            return;
        }

        if (!result.config().enabled) {
            JustEnoughAccidents.LOGGER.info("JEA is disabled by configuration for this server session.");
            return;
        }

        session = new JeaSession(server, result.config());
        session.start();
    }

    private static void stop(MinecraftServer server) {
        if (session != null) {
            session.close();
            session = null;
        }
    }

    private static void tick(MinecraftServer server) {
        var current = session;
        if (current != null) {
            current.tick();
        }
    }
}
