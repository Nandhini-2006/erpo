package com.datashield.core.model;

import java.io.Serializable;
import java.time.Duration;
import java.time.Instant;

/**
 * Summary record produced upon backup or restore execution completion.
 */
public class BackupExecutionSummary implements Serializable {
    private final String operation; // BACKUP or RESTORE
    private final String backupId;
    private final boolean success;
    private final Instant startTime;
    private final Instant endTime;
    private final long durationMillis;
    private final long rawBytes;
    private final long archiveBytes;
    private final String sha256Checksum;
    private final String destination;
    private final String errorMessage;

    public BackupExecutionSummary(String operation, String backupId, boolean success,
                                  Instant startTime, Instant endTime,
                                  long rawBytes, long archiveBytes,
                                  String sha256Checksum, String destination, String errorMessage) {
        this.operation = operation;
        this.backupId = backupId;
        this.success = success;
        this.startTime = startTime;
        this.endTime = endTime;
        this.durationMillis = startTime != null && endTime != null ? Duration.between(startTime, endTime).toMillis() : 0;
        this.rawBytes = rawBytes;
        this.archiveBytes = archiveBytes;
        this.sha256Checksum = sha256Checksum;
        this.destination = destination;
        this.errorMessage = errorMessage;
    }

    public String getOperation() {
        return operation;
    }

    public String getBackupId() {
        return backupId;
    }

    public boolean isSuccess() {
        return success;
    }

    public Instant getStartTime() {
        return startTime;
    }

    public Instant getEndTime() {
        return endTime;
    }

    public long getDurationMillis() {
        return durationMillis;
    }

    public long getRawBytes() {
        return rawBytes;
    }

    public long getArchiveBytes() {
        return archiveBytes;
    }

    public String getSha256Checksum() {
        return sha256Checksum;
    }

    public String getDestination() {
        return destination;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public double getThroughputMBps() {
        if (durationMillis <= 0) return 0;
        double seconds = durationMillis / 1000.0;
        double mb = (double) rawBytes / (1024.0 * 1024.0);
        return mb / seconds;
    }
}

