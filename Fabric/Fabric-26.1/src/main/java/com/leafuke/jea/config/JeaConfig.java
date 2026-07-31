package com.leafuke.jea.config;

import java.util.Locale;

public final class JeaConfig {
    public int schemaVersion = 1;
    public boolean enabled = true;
    public int cooldownSeconds = 60;
    public Backup backup = new Backup();
    public Detectors detectors = new Detectors();
    public Scoreboard scoreboard = new Scoreboard();

    public void validate() {
        require(schemaVersion == 1, "schemaVersion must be 1");
        requireRange(cooldownSeconds, 0, 3600, "cooldownSeconds");
        require(backup != null, "backup must be an object");
        require(detectors != null, "detectors must be an object");
        require(scoreboard != null, "scoreboard must be an object");
        backup.validate();
        detectors.validate();
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }

    private static void requireRange(int value, int minimum, int maximum, String field) {
        require(value >= minimum && value <= maximum,
                field + " must be between " + minimum + " and " + maximum);
    }

    private static void requireRange(double value, double minimum, double maximum, String field) {
        require(Double.isFinite(value) && value >= minimum && value <= maximum,
                field + " must be between " + minimum + " and " + maximum);
    }

    public static final class Backup {
        public String mode = "incremental";
        public String compressionMethod = "zstd";
        public int compressionLevel = 6;

        public void validate() {
            require(mode != null, "backup.mode must be a string");
            mode = mode.trim().toLowerCase(Locale.ROOT);
            require(mode.equals("full") || mode.equals("incremental"),
                    "backup.mode must be full or incremental");

            require(compressionMethod != null, "backup.compressionMethod must be a string");
            compressionMethod = normalizeCompressionMethod(compressionMethod);
            int minimum = switch (compressionMethod) {
                case "zstd", "BZip2" -> 1;
                default -> 0;
            };
            int maximum = compressionMethod.equals("zstd") ? 22 : 9;
            requireRange(compressionLevel, minimum, maximum, "backup.compressionLevel");
        }

        private static String normalizeCompressionMethod(String value) {
            return switch (value.trim().toLowerCase(Locale.ROOT)) {
                case "lzma2" -> "LZMA2";
                case "deflate" -> "Deflate";
                case "bzip2" -> "BZip2";
                case "zstd" -> "zstd";
                default -> throw new IllegalArgumentException(
                        "backup.compressionMethod must be LZMA2, Deflate, BZip2, or zstd");
            };
        }
    }

    public static final class Detectors {
        public Toggle fatalFall = new Toggle();
        public LowAir lowAir = new LowAir();
        public Toggle lava = new Toggle();
        public Elytra elytra = new Elytra();
        public LowHealth lowHealth = new LowHealth();
        public Toggle totem = new Toggle();
        public Creeper creeper = new Creeper();
        public Tnt tnt = new Tnt();
        public PetDanger petDanger = new PetDanger();

        public void validate() {
            require(fatalFall != null, "detectors.fatalFall must be an object");
            require(lowAir != null, "detectors.lowAir must be an object");
            require(lava != null, "detectors.lava must be an object");
            require(elytra != null, "detectors.elytra must be an object");
            require(lowHealth != null, "detectors.lowHealth must be an object");
            require(totem != null, "detectors.totem must be an object");
            require(creeper != null, "detectors.creeper must be an object");
            require(tnt != null, "detectors.tnt must be an object");
            require(petDanger != null, "detectors.petDanger must be an object");
            requireRange(lowAir.triggerAir, 0, 300, "detectors.lowAir.triggerAir");
            requireRange(lowAir.rearmAir, 0, 300, "detectors.lowAir.rearmAir");
            require(lowAir.rearmAir > lowAir.triggerAir,
                    "detectors.lowAir.rearmAir must be greater than triggerAir");
            requireRange(elytra.remainingDurability, 1, 432,
                    "detectors.elytra.remainingDurability");
            requireRange(lowHealth.effectiveHealth, Double.MIN_NORMAL, 1024.0,
                    "detectors.lowHealth.effectiveHealth");
            requireRange(creeper.normalRadius, 1.0, 64.0,
                    "detectors.creeper.normalRadius");
            requireRange(creeper.chargedRadius, 1.0, 64.0,
                    "detectors.creeper.chargedRadius");
            require(creeper.chargedRadius >= creeper.normalRadius,
                    "detectors.creeper.chargedRadius must be at least normalRadius");
            requireRange(tnt.radius, 1.0, 32.0,
                    "detectors.tnt.radius");
            requireRange(tnt.maxFuseTicks, 1, 80,
                    "detectors.tnt.maxFuseTicks");
            requireRange(petDanger.radius, 1.0, 64.0,
                    "detectors.petDanger.radius");
            requireRange(petDanger.healthThreshold, 0.01, 1.0,
                    "detectors.petDanger.healthThreshold");
        }
    }

    public static class Toggle {
        public boolean enabled = true;
    }

    public static final class LowAir extends Toggle {
        public int triggerAir = 60;
        public int rearmAir = 200;
    }

    public static final class Elytra extends Toggle {
        public int remainingDurability = 10;
    }

    public static final class LowHealth extends Toggle {
        public double effectiveHealth = 2.0;
    }

    public static final class Creeper extends Toggle {
        public double normalRadius = 6.0;
        public double chargedRadius = 12.0;
    }

    public static final class Tnt extends Toggle {
        public double radius = 12.0;
        public int maxFuseTicks = 40;
        public boolean excludeUnderwater = true;
    }

    public static final class PetDanger extends Toggle {
        public double radius = 32.0;
        public double healthThreshold = 0.25;
    }

    public static final class Scoreboard {
        public boolean enabled = true;
    }
}
