package com.leafuke.jea.incident;

public record DangerScanState(int eligiblePlayerCount, boolean activeDanger) {
    public DangerScanState {
        if (eligiblePlayerCount < 0) {
            throw new IllegalArgumentException("eligiblePlayerCount must not be negative");
        }
    }

    public boolean isQuiet() {
        return eligiblePlayerCount > 0 && !activeDanger;
    }
}
