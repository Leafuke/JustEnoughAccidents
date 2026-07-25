package com.leafuke.jea.incident;

import com.leafuke.jea.JustEnoughAccidents;
import com.leafuke.jea.config.JeaConfig;
import com.leafuke.minebackup.api.v2.BackupRequest;
import com.leafuke.minebackup.api.v2.BackupResult;
import com.leafuke.minebackup.api.v2.MineBackupApi;
import com.leafuke.minebackup.api.v2.OperationHandle;
import com.leafuke.minebackup.api.v2.OperationPresentation;

import java.util.Map;

public final class CreateNowBackupStrategy implements IncidentBackupStrategy {
    private final JeaConfig.Backup config;

    public CreateNowBackupStrategy(JeaConfig.Backup config) {
        this.config = config;
    }

    @Override
    public OperationHandle<BackupResult> submit(IncidentBatch batch) {
        var request = BackupRequest.create(JustEnoughAccidents.MOD_ID, batch.comment())
                .withParameters(Map.of(
                        "backup_mode", config.mode,
                        "compression_method", config.compressionMethod,
                        "compression_level", Integer.toString(config.compressionLevel)))
                .withPresentation(OperationPresentation.callerManaged());
        return MineBackupApi.getInstance().backupCurrent(request);
    }
}
