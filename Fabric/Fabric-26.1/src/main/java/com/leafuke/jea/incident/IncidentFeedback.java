package com.leafuke.jea.incident;

import com.leafuke.minebackup.api.v1.BackupResult;

public interface IncidentFeedback {
    void suppressedInFlight(IncidentBatch batch);

    void suppressedCooldown(IncidentBatch batch, long remainingMillis);

    void submissionFailed(IncidentBatch batch, Throwable throwable);

    void accepted(IncidentBatch batch);

    void completed(
            IncidentBatch batch,
            BackupResult result,
            Throwable throwable,
            long elapsedMillis);
}
