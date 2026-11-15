package com.datashield.core.model;

import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;

/**
 * Encapsulates all options and targets for a restore run.
 */
public class RestoreRequest implements Serializable {
    private ConnectionConfig targetConnectionConfig = new ConnectionConfig();
    private String backupSource; // Local path or cloud URL (s3://...)
    private String encryptionPassword;
    private Set<String> selectiveTables = new HashSet<>();
    private boolean dryRun = false;
    private boolean dropExistingTables = false;
    private String slackWebhookUrl;

    public RestoreRequest() {
    }

    public ConnectionConfig getTargetConnectionConfig() {
        return targetConnectionConfig;
    }

    public void setTargetConnectionConfig(ConnectionConfig targetConnectionConfig) {
        this.targetConnectionConfig = targetConnectionConfig;
    }

    public String getBackupSource() {
        return backupSource;
    }

    public void setBackupSource(String backupSource) {
        this.backupSource = backupSource;
    }

    public String getEncryptionPassword() {
        return encryptionPassword;
    }

    public void setEncryptionPassword(String encryptionPassword) {
        this.encryptionPassword = encryptionPassword;
    }

    public Set<String> getSelectiveTables() {
        return selectiveTables;
    }

    public void setSelectiveTables(Set<String> selectiveTables) {
        this.selectiveTables = selectiveTables;
    }

    public boolean isDryRun() {
        return dryRun;
    }

    public void setDryRun(boolean dryRun) {
        this.dryRun = dryRun;
    }

    public boolean isDropExistingTables() {
        return dropExistingTables;
    }

    public void setDropExistingTables(boolean dropExistingTables) {
        this.dropExistingTables = dropExistingTables;
    }

    public String getSlackWebhookUrl() {
        return slackWebhookUrl;
    }

    public void setSlackWebhookUrl(String slackWebhookUrl) {
        this.slackWebhookUrl = slackWebhookUrl;
    }

    public TableFilter toTableFilter() {
        return TableFilter.of(selectiveTables, null);
    }
}

