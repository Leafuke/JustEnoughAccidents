package com.leafuke.jea.incident;

import net.minecraft.server.level.ServerPlayer;

import java.util.Objects;
import java.util.UUID;

public record IncidentSignal(IncidentType type, UUID playerId, String playerName) {
    public IncidentSignal {
        Objects.requireNonNull(type, "type");
        playerName = playerName == null ? "" : playerName;
    }

    public static IncidentSignal player(IncidentType type, ServerPlayer player) {
        return new IncidentSignal(type, player.getUUID(), player.getScoreboardName());
    }

    public static IncidentSignal global(IncidentType type) {
        return new IncidentSignal(type, null, "");
    }
}
