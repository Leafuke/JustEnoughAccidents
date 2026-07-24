package com.leafuke.jea.incident;

import com.leafuke.minebackup.api.v1.BackupResult;
import com.leafuke.minebackup.api.v1.OperationHandle;

public interface IncidentBackupStrategy {
    OperationHandle<BackupResult> submit(IncidentBatch batch);
}
