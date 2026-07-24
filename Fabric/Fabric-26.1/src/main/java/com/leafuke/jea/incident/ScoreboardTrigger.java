package com.leafuke.jea.incident;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.scores.ScoreHolder;

public final class ScoreboardTrigger {
    public static final String OBJECTIVE = "jea_request";
    public static final String HOLDER = "#global";
    private static final ScoreHolder SCORE_HOLDER = ScoreHolder.forNameOnly(HOLDER);

    public boolean consume(MinecraftServer server) {
        var scoreboard = server.getScoreboard();
        var objective = scoreboard.getObjective(OBJECTIVE);
        if (objective == null) {
            return false;
        }

        var score = scoreboard.getPlayerScoreInfo(SCORE_HOLDER, objective);
        if (score == null || score.value() < 1) {
            return false;
        }

        scoreboard.getOrCreatePlayerScore(SCORE_HOLDER, objective).set(0);
        return true;
    }
}
