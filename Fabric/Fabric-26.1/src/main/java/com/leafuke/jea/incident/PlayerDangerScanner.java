package com.leafuke.jea.incident;

import com.leafuke.jea.config.JeaConfig;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.item.Items;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

public final class PlayerDangerScanner {
    private final JeaConfig.Detectors config;
    private final Map<UUID, EnumSet<IncidentType>> active = new HashMap<>();

    public PlayerDangerScanner(JeaConfig.Detectors config) {
        this.config = config;
    }

    public void scan(MinecraftServer server, Consumer<IncidentSignal> sink) {
        var online = new HashSet<UUID>();
        for (var player : server.getPlayerList().getPlayers()) {
            online.add(player.getUUID());
            if (!isEligible(player)) {
                active.remove(player.getUUID());
                continue;
            }

            update(player, IncidentType.FATAL_FALL,
                    config.fatalFall.enabled && isFatalFall(player), sink);
            updateLowAir(player, sink);
            update(player, IncidentType.LAVA,
                    config.lava.enabled && isLavaDanger(player), sink);
            update(player, IncidentType.ELYTRA,
                    config.elytra.enabled && isElytraDanger(player), sink);
            update(player, IncidentType.LOW_HEALTH,
                    config.lowHealth.enabled && effectiveHealth(player) <= config.lowHealth.effectiveHealth,
                    sink);
            update(player, IncidentType.CREEPER,
                    config.creeper.enabled && isCreeperDanger(player), sink);
            update(player, IncidentType.TNT,
                    config.tnt.enabled && isTntDanger(player), sink);
            update(player, IncidentType.PET_DANGER,
                    config.petDanger.enabled && isPetDanger(player), sink);
        }
        active.keySet().retainAll(online);
    }

    public boolean isEligible(ServerPlayer player) {
        return player.isAlive()
                && !player.isRemoved()
                && !player.isCreative()
                && !player.isSpectator()
                && !player.hasDisconnected()
                && player.connection != null;
    }

    public void clear() {
        active.clear();
    }

    private void update(
            ServerPlayer player,
            IncidentType type,
            boolean dangerous,
            Consumer<IncidentSignal> sink) {
        var playerActive = active.computeIfAbsent(
                player.getUUID(),
                ignored -> EnumSet.noneOf(IncidentType.class));
        if (dangerous) {
            if (playerActive.add(type)) {
                sink.accept(IncidentSignal.player(type, player));
            }
        } else {
            playerActive.remove(type);
        }
    }

    private void updateLowAir(ServerPlayer player, Consumer<IncidentSignal> sink) {
        var playerActive = active.computeIfAbsent(
                player.getUUID(),
                ignored -> EnumSet.noneOf(IncidentType.class));
        if (!config.lowAir.enabled) {
            playerActive.remove(IncidentType.LOW_AIR);
            return;
        }

        boolean environmentDangerous = player.isUnderWater()
                && !player.canBreatheUnderwater()
                && !player.hasEffect(MobEffects.WATER_BREATHING)
                && !player.hasEffect(MobEffects.CONDUIT_POWER);
        boolean triggered = environmentDangerous
                && player.getAirSupply() <= config.lowAir.triggerAir;
        if (triggered) {
            if (playerActive.add(IncidentType.LOW_AIR)) {
                sink.accept(IncidentSignal.player(IncidentType.LOW_AIR, player));
            }
        } else if (!environmentDangerous || player.getAirSupply() >= config.lowAir.rearmAir) {
            playerActive.remove(IncidentType.LOW_AIR);
        }
    }

    private boolean isFatalFall(ServerPlayer player) {
        if (player.onGround()
                || player.getDeltaMovement().y >= 0.0
                || player.isFallFlying()
                || player.isInWater()
                || player.isInLava()) {
            return false;
        }

        double safeDistance = player.getAttributeValue(Attributes.SAFE_FALL_DISTANCE);
        double multiplier = player.getAttributeValue(Attributes.FALL_DAMAGE_MULTIPLIER);
        int predictedDamage = Math.max(
                0,
                Mth.floor((player.fallDistance + 1.0E-6 - safeDistance) * multiplier));
        return predictedDamage >= effectiveHealth(player);
    }

    private static boolean isLavaDanger(ServerPlayer player) {
        return player.isInLava() && !player.hasEffect(MobEffects.FIRE_RESISTANCE);
    }

    private boolean isElytraDanger(ServerPlayer player) {
        if (!player.isFallFlying()) {
            return false;
        }
        var chest = player.getItemBySlot(EquipmentSlot.CHEST);
        if (chest.getItem() != Items.ELYTRA || !chest.isDamageableItem()) {
            return false;
        }
        int remaining = chest.getMaxDamage() - chest.getDamageValue();
        return remaining <= config.elytra.remainingDurability;
    }

    private boolean isCreeperDanger(ServerPlayer player) {
        double maximumRadius = config.creeper.chargedRadius;
        var box = player.getBoundingBox().inflate(maximumRadius);
        for (Creeper creeper : player.level().getEntitiesOfClass(
                Creeper.class,
                box,
                entity -> entity.isAlive() && entity.getSwellDir() > 0)) {
            double radius = creeper.isPowered()
                    ? config.creeper.chargedRadius
                    : config.creeper.normalRadius;
            if (player.distanceToSqr(creeper) <= radius * radius) {
                return true;
            }
        }
        return false;
    }

    private boolean isTntDanger(ServerPlayer player) {
        double radius = config.tnt.radius;
        var box = player.getBoundingBox().inflate(radius);
        for (PrimedTnt tnt : player.level().getEntitiesOfClass(
                PrimedTnt.class,
                box,
                entity -> entity.isAlive())) {
            // 检查 TNT 的引信时间
            if (tnt.getFuse() > config.tnt.maxFuseTicks) {
                continue;
            }
            // 如果配置了排除水中 TNT，检查是否在水中
            if (config.tnt.excludeUnderwater && tnt.isInWater()) {
                continue;
            }
            // 检查距离
            if (player.distanceToSqr(tnt) <= radius * radius) {
                return true;
            }
        }
        return false;
    }

    private boolean isPetDanger(ServerPlayer player) {
        double radius = config.petDanger.radius;
        var box = player.getBoundingBox().inflate(radius);

        // 检查可驯服的动物（狼、猫、鹦鹉等）
        for (TamableAnimal tamable : player.level().getEntitiesOfClass(
                TamableAnimal.class,
                box,
                entity -> entity.isAlive() && entity.isTame())) {
            if (tamable.getOwner() == player) {
                float health = tamable.getHealth();
                float maxHealth = tamable.getMaxHealth();
                if (health / maxHealth <= config.petDanger.healthThreshold) {
                    return true;
                }
            }
        }

        return false;
    }

    private static double effectiveHealth(ServerPlayer player) {
        return player.getHealth() + player.getAbsorptionAmount();
    }
}
