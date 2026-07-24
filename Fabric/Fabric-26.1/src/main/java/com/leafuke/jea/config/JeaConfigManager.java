package com.leafuke.jea.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public final class JeaConfigManager {
    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .create();
    private static final Path CONFIG_PATH = FabricLoader.getInstance()
            .getConfigDir()
            .resolve("just-enough-accidents.json");

    private JeaConfigManager() {
    }

    public static LoadResult load() {
        try {
            if (Files.notExists(CONFIG_PATH)) {
                var defaults = new JeaConfig();
                defaults.validate();
                Files.createDirectories(CONFIG_PATH.getParent());
                Files.writeString(
                        CONFIG_PATH,
                        GSON.toJson(defaults) + System.lineSeparator(),
                        StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE_NEW,
                        StandardOpenOption.WRITE);
                return LoadResult.success(defaults);
            }

            JsonElement root = JsonParser.parseString(
                    Files.readString(CONFIG_PATH, StandardCharsets.UTF_8));
            validateTypes(root);
            var config = GSON.fromJson(root, JeaConfig.class);
            if (config == null) {
                return LoadResult.failure("configuration root must be a JSON object");
            }
            config.validate();
            return LoadResult.success(config);
        } catch (JsonParseException | IllegalArgumentException ex) {
            return LoadResult.failure(ex.getMessage());
        } catch (IOException ex) {
            return LoadResult.failure("could not read or create " + CONFIG_PATH + ": " + ex.getMessage());
        }
    }

    private static void validateTypes(JsonElement root) {
        if (root == null || !root.isJsonObject()) {
            throw new IllegalArgumentException("configuration root must be a JSON object");
        }
        var object = root.getAsJsonObject();
        integer(object, "schemaVersion", "schemaVersion");
        bool(object, "enabled", "enabled");
        integer(object, "cooldownSeconds", "cooldownSeconds");

        var backup = object(object, "backup", "backup");
        if (backup != null) {
            string(backup, "mode", "backup.mode");
            string(backup, "compressionMethod", "backup.compressionMethod");
            integer(backup, "compressionLevel", "backup.compressionLevel");
        }

        var detectors = object(object, "detectors", "detectors");
        if (detectors != null) {
            detectorToggle(detectors, "fatalFall");
            var lowAir = detectorToggle(detectors, "lowAir");
            if (lowAir != null) {
                integer(lowAir, "triggerAir", "detectors.lowAir.triggerAir");
                integer(lowAir, "rearmAir", "detectors.lowAir.rearmAir");
            }
            detectorToggle(detectors, "lava");
            var elytra = detectorToggle(detectors, "elytra");
            if (elytra != null) {
                integer(elytra, "remainingDurability", "detectors.elytra.remainingDurability");
            }
            var lowHealth = detectorToggle(detectors, "lowHealth");
            if (lowHealth != null) {
                number(lowHealth, "effectiveHealth", "detectors.lowHealth.effectiveHealth");
            }
            detectorToggle(detectors, "totem");
            var creeper = detectorToggle(detectors, "creeper");
            if (creeper != null) {
                number(creeper, "normalRadius", "detectors.creeper.normalRadius");
                number(creeper, "chargedRadius", "detectors.creeper.chargedRadius");
            }
        }

        var scoreboard = object(object, "scoreboard", "scoreboard");
        if (scoreboard != null) {
            bool(scoreboard, "enabled", "scoreboard.enabled");
        }
    }

    private static JsonObject detectorToggle(JsonObject detectors, String name) {
        var detector = object(detectors, name, "detectors." + name);
        if (detector != null) {
            bool(detector, "enabled", "detectors." + name + ".enabled");
        }
        return detector;
    }

    private static JsonObject object(JsonObject parent, String name, String path) {
        JsonElement value = parent.get(name);
        if (value == null) {
            return null;
        }
        if (!value.isJsonObject()) {
            throw new IllegalArgumentException(path + " must be an object");
        }
        return value.getAsJsonObject();
    }

    private static void bool(JsonObject parent, String name, String path) {
        JsonElement value = parent.get(name);
        if (value != null
                && (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean())) {
            throw new IllegalArgumentException(path + " must be a boolean");
        }
    }

    private static void string(JsonObject parent, String name, String path) {
        JsonElement value = parent.get(name);
        if (value != null
                && (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString())) {
            throw new IllegalArgumentException(path + " must be a string");
        }
    }

    private static void integer(JsonObject parent, String name, String path) {
        JsonElement value = parent.get(name);
        if (value == null) {
            return;
        }
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException(path + " must be an integer");
        }
        try {
            if (new BigDecimal(value.getAsString()).stripTrailingZeros().scale() > 0) {
                throw new IllegalArgumentException(path + " must be an integer");
            }
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(path + " must be an integer");
        }
    }

    private static void number(JsonObject parent, String name, String path) {
        JsonElement value = parent.get(name);
        if (value != null
                && (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber())) {
            throw new IllegalArgumentException(path + " must be a number");
        }
    }

    public record LoadResult(JeaConfig config, String error) {
        public static LoadResult success(JeaConfig config) {
            return new LoadResult(config, "");
        }

        public static LoadResult failure(String error) {
            return new LoadResult(null, error == null || error.isBlank()
                    ? "unknown configuration error"
                    : error);
        }

        public boolean isSuccess() {
            return config != null;
        }
    }
}
