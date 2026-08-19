package com.leafuke.jea.incident;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RestoreClickModeTest {
    @Test
    void integratedServerUsesRunCommand() {
        assertEquals(RestoreClickMode.RUN_COMMAND, RestoreClickMode.resolve(false, false));
    }

    @Test
    void dedicatedServerWithRestoreUsesSuggestCommand() {
        assertEquals(RestoreClickMode.SUGGEST_COMMAND, RestoreClickMode.resolve(true, true));
    }

    @Test
    void dedicatedServerWithoutRestoreHasNoAction() {
        assertEquals(RestoreClickMode.UNAVAILABLE, RestoreClickMode.resolve(true, false));
    }
}
