package com.leafuke.jea.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
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
                        StandardOpenOption.CREATE_NEW);
                return LoadResult.success(defaults);
            }

            var config = GSON.fromJson(Files.readString(CONFIG_PATH, StandardCharsets.UTF_8), JeaConfig.class);
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
