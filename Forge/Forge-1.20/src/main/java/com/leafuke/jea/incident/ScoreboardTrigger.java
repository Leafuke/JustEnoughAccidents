package com.leafuke.jea.incident;

import net.minecraft.server.MinecraftServer;

public final class ScoreboardTrigger {
    public static final String OBJECTIVE = "jea_request";
    public static final String HOLDER = "#global";

    public boolean consume(MinecraftServer server) {
        var scoreboard = server.getScoreboard();
        var objective = scoreboard.getObjective(OBJECTIVE);
        if (objective == null) {
            return false;
        }

        var score = scoreboard.getPlayerScores(HOLDER).get(objective);
        if (score == null || score.getScore() < 1) {
            return false;
        }

        score.setScore(0);
        return true;
    }
}
