package com.datashield.notify;

import com.datashield.core.model.BackupExecutionSummary;
import com.datashield.core.model.BackupManifest;

public interface NotificationService {
    void notifyBackupCompleted(BackupExecutionSummary summary, BackupManifest manifest);
    void notifyRestoreCompleted(BackupExecutionSummary summary);
}

