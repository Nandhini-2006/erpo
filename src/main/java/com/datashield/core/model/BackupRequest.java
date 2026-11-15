package com.datashield.core.model;

import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;

/**
 * Encapsulates all options and targets for a backup run.
 */
public class BackupRequest implements Serializable {
    private ConnectionConfig connectionConfig = new ConnectionConfig();
    private BackupType backupType = BackupType.FULL;
    private CompressionType compression = CompressionType.GZIP;
    private String encryptionPassword;
    private String storageDestination = "./backups";
    private Set<String> includeTables = new HashSet<>();
    private Set<String> excludeTables = new HashSet<>();
    private String slackWebhookUrl;
    private boolean dryRun = false;
    private int retentionDays = 0;

    public BackupRequest() {
    }

    public ConnectionConfig getConnectionConfig() {
        return connectionConfig;
    }

    public void setConnectionConfig(ConnectionConfig connectionConfig) {
        this.connectionConfig = connectionConfig;
    }

    public BackupType getBackupType() {
        return backupType;
    }

    public void setBackupType(BackupType backupType) {
        this.backupType = backupType;
    }

    public CompressionType getCompression() {
        return compression;
    }

    public void setCompression(CompressionType compression) {
        this.compression = compression;
    }

    public String getEncryptionPassword() {
        return encryptionPassword;
    }

    public void setEncryptionPassword(String encryptionPassword) {
        this.encryptionPassword = encryptionPassword;
    }

    public String getStorageDestination() {
        return storageDestination;
    }

    public void setStorageDestination(String storageDestination) {
        this.storageDestination = storageDestination;
    }

    public Set<String> getIncludeTables() {
        return includeTables;
    }

    public void setIncludeTables(Set<String> includeTables) {
        this.includeTables = includeTables;
    }

    public Set<String> getExcludeTables() {
        return excludeTables;
    }

    public void setExcludeTables(Set<String> excludeTables) {
        this.excludeTables = excludeTables;
    }

    public String getSlackWebhookUrl() {
        return slackWebhookUrl;
    }

    public void setSlackWebhookUrl(String slackWebhookUrl) {
        this.slackWebhookUrl = slackWebhookUrl;
    }

    public boolean isDryRun() {
        return dryRun;
    }

    public void setDryRun(boolean dryRun) {
        this.dryRun = dryRun;
    }

    public int getRetentionDays() {
        return retentionDays;
    }

    public void setRetentionDays(int retentionDays) {
        this.retentionDays = retentionDays;
    }

    public TableFilter toTableFilter() {
        return TableFilter.of(includeTables, excludeTables);
    }
}

