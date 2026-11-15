package com.datashield.core.model;

/**
 * Supported backup execution strategies.
 */
public enum BackupType {
    /**
     * Complete capture of all target database structures and data.
     */
    FULL("Full Backup"),

    /**
     * Captures only changes that occurred since the immediately preceding backup (Full or Incremental).
     */
    INCREMENTAL("Incremental Backup"),

    /**
     * Captures all changes that occurred since the last FULL backup.
     */
    DIFFERENTIAL("Differential Backup");

    private final String description;

    BackupType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    public static BackupType fromString(String value) {
        if (value == null || value.isBlank()) {
            return FULL;
        }
        return BackupType.valueOf(value.trim().toUpperCase());
    }
}

