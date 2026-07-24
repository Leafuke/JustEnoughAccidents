package com.leafuke.jea.incident;

import com.leafuke.jea.JustEnoughAccidents;
import com.leafuke.jea.config.JeaConfig;
import com.leafuke.minebackup.api.v1.BackupRequest;
import com.leafuke.minebackup.api.v1.BackupResult;
import com.leafuke.minebackup.api.v1.MineBackupApi;
import com.leafuke.minebackup.api.v1.OperationHandle;

import java.util.Map;
import java.util.Optional;

public final class CreateNowBackupStrategy implements IncidentBackupStrategy {
    private final JeaConfig.Backup config;

    public CreateNowBackupStrategy(JeaConfig.Backup config) {
        this.config = config;
    }

    @Override
    public OperationHandle<BackupResult> submit(IncidentBatch batch) {
        var request = new BackupRequest(
                JustEnoughAccidents.MOD_ID,
                Optional.of(batch.comment()),
                Map.of(
                        "backup_mode", config.mode,
                        "compression_method", config.compressionMethod,
                        "compression_level", Integer.toString(config.compressionLevel)));
        return MineBackupApi.getInstance().backupCurrent(request);
    }
}
