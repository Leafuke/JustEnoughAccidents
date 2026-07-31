package com.leafuke.jea.incident;

import net.minecraft.server.level.ServerPlayer;

import java.util.Objects;
import java.util.UUID;

public final class IncidentSignal {
    private final IncidentType type;
    private final UUID playerId;
    private final String playerName;

    public IncidentSignal(IncidentType type, UUID playerId, String playerName) {
        this.type = Objects.requireNonNull(type, "type");
        this.playerId = playerId;
        this.playerName = playerName == null ? "" : playerName;
    }

    public static IncidentSignal player(IncidentType type, ServerPlayer player) {
        return new IncidentSignal(type, player.getUUID(), player.getScoreboardName());
    }

    public static IncidentSignal global(IncidentType type) {
        return new IncidentSignal(type, null, "");
    }

    public IncidentType type() {
        return type;
    }

    public UUID playerId() {
        return playerId;
    }

    public String playerName() {
        return playerName;
    }
}
