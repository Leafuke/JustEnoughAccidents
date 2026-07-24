package com.leafuke.jea.incident;

public enum IncidentType {
    FATAL_FALL("fatal_fall"),
    LOW_AIR("low_air"),
    LAVA("lava"),
    ELYTRA("elytra"),
    LOW_HEALTH("low_health"),
    TOTEM("totem"),
    CREEPER("creeper"),
    SCOREBOARD("scoreboard");

    private final String id;

    IncidentType(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }
}
