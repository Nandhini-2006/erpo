package com.datashield.cli.config;

import com.datashield.core.model.BackupType;
import com.datashield.core.model.CompressionType;
import com.datashield.core.model.DatabaseType;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;

@JsonIgnoreProperties(ignoreUnknown = true)
public class CliConfig implements Serializable {
    @JsonProperty("database_type")
    private DatabaseType databaseType = DatabaseType.MYSQL;

    private String host = "localhost";
    private int port = 3306;
    private String database;
    private String username;
    private String password;
    private String uri;

    @JsonProperty("backup_type")
    private BackupType backupType = BackupType.FULL;

    private CompressionType compression = CompressionType.GZIP;

    @JsonProperty("encryption_password")
    private String encryptionPassword;

    private String destination = "./backups";

    @JsonProperty("include_tables")
    private Set<String> includeTables = new HashSet<>();

    @JsonProperty("exclude_tables")
    private Set<String> excludeTables = new HashSet<>();

    @JsonProperty("retention_days")
    private int retentionDays = 30;

    @JsonProperty("slack_webhook")
    private String slackWebhook;

    public CliConfig() {
    }

    public DatabaseType getDatabaseType() {
        return databaseType;
    }

    public void setDatabaseType(DatabaseType databaseType) {
        this.databaseType = databaseType;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getDatabase() {
        return database;
    }

    public void setDatabase(String database) {
        this.database = database;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getUri() {
        return uri;
    }

    public void setUri(String uri) {
        this.uri = uri;
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

    public String getDestination() {
        return destination;
    }

    public void setDestination(String destination) {
        this.destination = destination;
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

    public int getRetentionDays() {
        return retentionDays;
    }

    public void setRetentionDays(int retentionDays) {
        this.retentionDays = retentionDays;
    }

    public String getSlackWebhook() {
        return slackWebhook;
    }

    public void setSlackWebhook(String slackWebhook) {
        this.slackWebhook = slackWebhook;
    }
}

