package com.leafuke.jea.incident;

public enum RestoreClickMode {
    RUN_COMMAND,
    SUGGEST_COMMAND,
    UNAVAILABLE;

    public static RestoreClickMode resolve(boolean dedicatedServer, boolean dedicatedRestoreAvailable) {
        if (!dedicatedServer) {
            return RUN_COMMAND;
        }
        return dedicatedRestoreAvailable ? SUGGEST_COMMAND : UNAVAILABLE;
    }
}
