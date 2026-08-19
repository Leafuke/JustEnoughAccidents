package com.leafuke.jea.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JeaConfigManagerTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void legacyConfigurationUsesEnabledSafeAnchorDefaultsWithoutRewriting() throws IOException {
        String legacyConfiguration = """
                {
                  "schemaVersion": 1,
                  "enabled": true
                }
                """;
        Path configPath = writeConfiguration(legacyConfiguration);

        var result = JeaConfigManager.load(configPath);

        assertTrue(result.isSuccess(), result.error());
        assertTrue(result.config().safeAnchor.enabled);
        assertEquals(30, result.config().safeAnchor.refreshMinutes);
        assertEquals(30, result.config().safeAnchor.quietSeconds);
        assertEquals(legacyConfiguration, Files.readString(configPath));
    }

    @Test
    void explicitSafeAnchorDisabledIsRespected() throws IOException {
        var result = JeaConfigManager.load(writeConfiguration("""
                { "schemaVersion": 1, "safeAnchor": { "enabled": false } }
                """));

        assertTrue(result.isSuccess(), result.error());
        assertFalse(result.config().safeAnchor.enabled);
        assertEquals(30, result.config().safeAnchor.refreshMinutes);
        assertEquals(30, result.config().safeAnchor.quietSeconds);
    }

    @Test
    void invalidSafeAnchorEnabledTypeFails() throws IOException {
        assertInvalid("""
                { "schemaVersion": 1, "safeAnchor": { "enabled": "yes" } }
                """, "safeAnchor.enabled must be a boolean");
    }

    @Test
    void refreshMinutesOutsideSupportedRangeFails() throws IOException {
        assertInvalid("""
                { "schemaVersion": 1, "safeAnchor": { "refreshMinutes": 0 } }
                """, "safeAnchor.refreshMinutes must be between 1 and 10080");
    }

    @Test
    void quietSecondsOutsideSupportedRangeFails() throws IOException {
        assertInvalid("""
                { "schemaVersion": 1, "safeAnchor": { "quietSeconds": 3601 } }
                """, "safeAnchor.quietSeconds must be between 0 and 3600");
    }

    private void assertInvalid(String configuration, String expectedError) throws IOException {
        var result = JeaConfigManager.load(writeConfiguration(configuration));

        assertFalse(result.isSuccess());
        assertEquals(expectedError, result.error());
    }

    private Path writeConfiguration(String configuration) throws IOException {
        Path configPath = temporaryDirectory.resolve("just-enough-accidents.json");
        Files.writeString(configPath, configuration);
        return configPath;
    }
}
