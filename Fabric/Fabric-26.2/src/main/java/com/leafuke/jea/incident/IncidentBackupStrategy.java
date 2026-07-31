package com.leafuke.jea.incident;

import com.leafuke.minebackup.api.v2.BackupResult;
import com.leafuke.minebackup.api.v2.OperationHandle;

public interface IncidentBackupStrategy {
    OperationHandle<BackupResult> submit(IncidentBatch batch);
}
