package com.leafuke.jea.incident;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.leafuke.jea.JustEnoughAccidents;
import com.leafuke.jea.anchor.SafeAnchorCoordinator;
import com.leafuke.minebackup.api.v2.BackupEntry;
import com.leafuke.minebackup.api.v2.BackupId;
import com.leafuke.minebackup.api.v2.BackupResult;
import com.leafuke.minebackup.api.v2.MineBackupApi;
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
        var clickMode = restoreClickMode();
        if (clickMode == RestoreClickMode.UNAVAILABLE) {
            sendRecoveryMessage(batch, Component.translatable(
                    "just_enough_accidents.message.dedicated_restore_unavailable",
                    MineBackupApi.getInstance().runtimeStatus()
                            .dedicatedRestoreUnavailableReason()
                            .orElse("unknown")).withStyle(ChatFormatting.YELLOW));
            return;
        }
        if (!safeAnchorCoordinator.enabled()) {
            sendIncidentRestoreButton(batch, incidentBackupId, clickMode);
            return;
        }
        safeAnchorCoordinator.lookupRecovery(
                batch.detectedAt(),
                safeAnchor -> sendRecoveryTargets(batch, incidentBackupId, safeAnchor, clickMode),
                throwable -> {
                    JustEnoughAccidents.LOGGER.warn(
                            "Could not find a pre-incident JEA safe anchor for {}",
                            batch.comment(), throwable);
                    sendRecoveryMessage(batch, Component.translatable(
                            "just_enough_accidents.message.safe_anchor_lookup_failed")
                            .withStyle(ChatFormatting.YELLOW));
                    sendIncidentRestoreButton(batch, incidentBackupId, clickMode);
                });
    }

    private void sendRecoveryTargets(
            IncidentBatch batch,
            BackupId incidentBackupId,
            Optional<BackupEntry> safeAnchor,
            RestoreClickMode clickMode) {
        for (var player : recoveryTargets(batch)) {
            safeAnchor.ifPresentOrElse(
                    anchor -> player.sendSystemMessage(restoreButton(
                            anchor.backupId(), "just_enough_accidents.action.restore_safe_anchor", clickMode)),
                    () -> player.sendSystemMessage(Component.translatable(
                            "just_enough_accidents.message.safe_anchor_unavailable")
                            .withStyle(ChatFormatting.YELLOW)));
            player.sendSystemMessage(restoreButton(
                    incidentBackupId, "just_enough_accidents.action.restore_incident_site", clickMode));
        }
    }

    private void sendIncidentRestoreButton(
            IncidentBatch batch,
            BackupId backupId,
            RestoreClickMode clickMode) {
        for (var player : recoveryTargets(batch)) {
            player.sendSystemMessage(restoreButton(
                    backupId, "just_enough_accidents.action.restore_incident_site", clickMode));
        }
    }

    private void sendRecoveryMessage(IncidentBatch batch, Component message) {
        for (var player : recoveryTargets(batch)) {
            player.sendSystemMessage(message);
        }
    }

    private Set<ServerPlayer> recoveryTargets(IncidentBatch batch) {
        if (server.isDedicatedServer()) {
            return targets(batch, false);
        }
        var owner = owner();
        return owner == null ? Set.of() : Set.of(owner);
    }

    private RestoreClickMode restoreClickMode() {
        boolean dedicated = server.isDedicatedServer();
        boolean dedicatedRestoreAvailable = !dedicated
                || MineBackupApi.getInstance().runtimeStatus().dedicatedRestoreAvailable();
        return RestoreClickMode.resolve(dedicated, dedicatedRestoreAvailable);
    }

    private static Component restoreButton(
            BackupId backupId,
            String translationKey,
            RestoreClickMode clickMode) {
        String command = "/mb restore " + StringArgumentType.escapeIfRequired(backupId.value());
        return Component.translatable(translationKey)
                .withStyle(style -> style
                        .withColor(ChatFormatting.GREEN)
                        .withUnderlined(true)
                        .withClickEvent(switch (clickMode) {
                            case RUN_COMMAND -> new ClickEvent.RunCommand(command);
                            case SUGGEST_COMMAND -> new ClickEvent.SuggestCommand(command);
                            case UNAVAILABLE -> throw new IllegalStateException("restore action is unavailable");
                        }));
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
