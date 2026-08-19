package com.leafuke.jea.incident;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.leafuke.jea.JustEnoughAccidents;
import com.leafuke.jea.anchor.SafeAnchorCoordinator;
import com.leafuke.minebackup.api.v2.BackupEntry;
import com.leafuke.minebackup.api.v2.BackupId;
import com.leafuke.minebackup.api.v2.BackupResult;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

public final class IncidentNotifier implements IncidentFeedback {
    private final MinecraftServer server;
    private final SafeAnchorCoordinator safeAnchorCoordinator;

    public IncidentNotifier(MinecraftServer server, SafeAnchorCoordinator safeAnchorCoordinator) {
        this.server = server;
        this.safeAnchorCoordinator = safeAnchorCoordinator;
    }

    @Override
    public void suppressedInFlight(IncidentBatch batch) {
        send(batch, false, Component.translatable(
                "just_enough_accidents.message.suppressed_in_flight",
                reasons(batch)).withStyle(ChatFormatting.YELLOW));
    }

    @Override
    public void suppressedCooldown(IncidentBatch batch, long remainingMillis) {
        long seconds = Math.max(1L, (remainingMillis + 999L) / 1000L);
        send(batch, false, Component.translatable(
                "just_enough_accidents.message.suppressed_cooldown",
                reasons(batch),
                seconds).withStyle(ChatFormatting.YELLOW));
    }

    @Override
    public void submissionFailed(IncidentBatch batch, Throwable throwable) {
        send(batch, false, Component.translatable(
                "just_enough_accidents.message.failed",
                reasons(batch),
                safeMessage(throwable)).withStyle(ChatFormatting.RED));
    }

    @Override
    public void accepted(IncidentBatch batch) {
        send(batch, false, Component.translatable(
                "just_enough_accidents.message.accepted",
                reasons(batch)).withStyle(ChatFormatting.YELLOW));
    }

    @Override
    public void completed(
            IncidentBatch batch,
            BackupResult result,
            Throwable throwable,
            long elapsedMillis) {
        if (throwable != null) {
            submissionFailed(batch, throwable);
            return;
        }

        switch (result.outcome()) {
            case CREATED -> {
                send(batch, true, Component.translatable(
                        "just_enough_accidents.message.created",
                        reasons(batch),
                        elapsedMillis).withStyle(ChatFormatting.GREEN));
                result.backupId().ifPresent(backupId -> offerRecoveryTargets(batch, backupId));
            }
            case NO_CHANGES -> send(batch, false, Component.translatable(
                    "just_enough_accidents.message.no_changes",
                    reasons(batch),
                    elapsedMillis).withStyle(ChatFormatting.GRAY));
            case CANCELLED -> send(batch, false, Component.translatable(
                    "just_enough_accidents.message.cancelled",
                    reasons(batch),
                    failureMessage(result)).withStyle(ChatFormatting.YELLOW));
            case REJECTED -> send(batch, false, Component.translatable(
                    "just_enough_accidents.message.rejected",
                    reasons(batch),
                    failureMessage(result)).withStyle(ChatFormatting.RED));
            case FAILED -> send(batch, false, Component.translatable(
                    "just_enough_accidents.message.failed",
                    reasons(batch),
                    failureMessage(result)).withStyle(ChatFormatting.RED));
        }
    }

    private void send(IncidentBatch batch, boolean includeOwner, Component message) {
        for (var player : targets(batch, includeOwner)) {
            player.sendSystemMessage(message);
        }
    }

    private Set<ServerPlayer> targets(IncidentBatch batch, boolean includeOwner) {
        var players = new LinkedHashSet<ServerPlayer>();
        for (var id : batch.playerIds()) {
            var player = server.getPlayerList().getPlayer(id);
            if (player != null) {
                players.add(player);
            }
        }
        if (players.isEmpty() || includeOwner) {
            var owner = owner();
            if (owner != null) {
                players.add(owner);
            }
        }
        return players;
    }

    private ServerPlayer owner() {
        var profile = server.getSingleplayerProfile();
        return profile == null ? null : server.getPlayerList().getPlayer(profile.id());
    }

    private void offerRecoveryTargets(IncidentBatch batch, BackupId incidentBackupId) {
        if (!safeAnchorCoordinator.enabled()) {
            sendIncidentRestoreButtonToOwner(incidentBackupId);
            return;
        }
        safeAnchorCoordinator.lookupRecovery(
                batch.detectedAt(),
                safeAnchor -> sendRecoveryTargets(incidentBackupId, safeAnchor),
                throwable -> {
                    JustEnoughAccidents.LOGGER.warn(
                            "Could not find a pre-incident JEA safe anchor for {}",
                            batch.comment(), throwable);
                    var owner = owner();
                    if (owner != null) {
                        owner.sendSystemMessage(Component.translatable(
                                "just_enough_accidents.message.safe_anchor_lookup_failed")
                                .withStyle(ChatFormatting.YELLOW));
                    }
                    sendIncidentRestoreButtonToOwner(incidentBackupId);
                });
    }

    private void sendRecoveryTargets(BackupId incidentBackupId, Optional<BackupEntry> safeAnchor) {
        var owner = owner();
        if (owner == null) {
            return;
        }
        safeAnchor.ifPresentOrElse(
                anchor -> owner.sendSystemMessage(restoreButton(
                        anchor.backupId(), "just_enough_accidents.action.restore_safe_anchor")),
                () -> owner.sendSystemMessage(Component.translatable(
                        "just_enough_accidents.message.safe_anchor_unavailable")
                        .withStyle(ChatFormatting.YELLOW)));
        owner.sendSystemMessage(restoreButton(
                incidentBackupId, "just_enough_accidents.action.restore_incident_site"));
    }

    private void sendIncidentRestoreButtonToOwner(BackupId backupId) {
        var owner = owner();
        if (owner != null) {
            owner.sendSystemMessage(restoreButton(backupId, "just_enough_accidents.action.restore_incident_site"));
        }
    }

    private static Component restoreButton(BackupId backupId, String translationKey) {
        String command = "/mb restore " + StringArgumentType.escapeIfRequired(backupId.value());
        return Component.translatable(translationKey)
                .withStyle(style -> style
                        .withColor(ChatFormatting.GREEN)
                        .withUnderlined(true)
                        .withClickEvent(new ClickEvent.RunCommand(command)));
    }

    private static MutableComponent reasons(IncidentBatch batch) {
        MutableComponent result = Component.empty();
        boolean first = true;
        for (var type : batch.types()) {
            if (!first) {
                result.append(", ");
            }
            result.append(Component.translatable(type.translationKey()));
            first = false;
        }
        return result;
    }

    private static String failureMessage(BackupResult result) {
        return result.failure()
                .map(failure -> failure.code() + ": " + failure.message())
                .filter(value -> !value.isBlank())
                .orElse("unknown");
    }

    private static String safeMessage(Throwable throwable) {
        return throwable.getMessage() == null || throwable.getMessage().isBlank()
                ? throwable.getClass().getSimpleName()
                : throwable.getMessage();
    }
}
