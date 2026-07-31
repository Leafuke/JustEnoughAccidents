package com.leafuke.jea.runtime;

import com.leafuke.jea.JustEnoughAccidents;
import com.leafuke.jea.config.JeaConfigManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public final class JeaRuntime {
    private static JeaSession session;

    private JeaRuntime() {
    }

    public static void register() {
        MinecraftForge.EVENT_BUS.register(new EventHandler());
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

    private static final class EventHandler {
        @SubscribeEvent
        public void onServerStarting(ServerStartingEvent event) {
            start(event.getServer());
        }

        @SubscribeEvent
        public void onServerStopping(ServerStoppingEvent event) {
            stop(event.getServer());
        }

        @SubscribeEvent
        public void onServerStopped(ServerStoppedEvent event) {
            session = null;
        }

        @SubscribeEvent
        public void onServerTick(TickEvent.ServerTickEvent event) {
            if (event.phase == TickEvent.Phase.END) {
                tick(event.getServer());
            }
        }
    }
}
