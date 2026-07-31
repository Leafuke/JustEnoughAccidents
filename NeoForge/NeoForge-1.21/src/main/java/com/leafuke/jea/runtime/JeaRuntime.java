package com.leafuke.jea.runtime;

import com.leafuke.jea.JustEnoughAccidents;
import com.leafuke.jea.config.JeaConfigManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

public final class JeaRuntime {
    private static JeaSession session;

    private JeaRuntime() {
    }

    public static void register(IEventBus modBus) {
        // Register to the game event bus (NeoForge events)
        NeoForge.EVENT_BUS.addListener(JeaRuntime::onServerStarting);
        NeoForge.EVENT_BUS.addListener(JeaRuntime::onServerStopping);
        NeoForge.EVENT_BUS.addListener(JeaRuntime::onServerStopped);
        NeoForge.EVENT_BUS.addListener(JeaRuntime::onServerTick);
    }

    public static void signalTotem(ServerPlayer player) {
        var current = session;
        if (current != null) {
            current.signalTotem(player);
        }
    }

    private static void onServerStarting(ServerStartingEvent event) {
        start(event.getServer());
    }

    private static void onServerStopping(ServerStoppingEvent event) {
        stop(event.getServer());
    }

    private static void onServerStopped(ServerStoppedEvent event) {
        session = null;
    }

    private static void onServerTick(ServerTickEvent.Post event) {
        tick(event.getServer());
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
