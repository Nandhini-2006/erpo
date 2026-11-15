package com.datashield.core.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.io.Serializable;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Enterprise manifest describing a backup archive's pedigree, cryptographic hash,
 * lineage, size, and database state coordinates (binlog/LSN).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class BackupManifest implements Serializable {
    private String backupId;
    private String parentBackupId;
    private BackupType backupType = BackupType.FULL;
    private DatabaseType databaseType;
    private String databaseName;
    private String serverHost;
    private int serverPort;

    @JsonFormat(shape = JsonFormat.Shape.STRING, timezone = "UTC")
    private Instant startTime;

    @JsonFormat(shape = JsonFormat.Shape.STRING, timezone = "UTC")
    private Instant endTime;

    private long durationMillis;
    private long rawBytes;
    private long archiveBytes;
    private String sha256Checksum;
    private CompressionType compression = CompressionType.GZIP;
    private String encryptionAlgorithm = "NONE";
    private String storageLocation;
    private List<String> includedTables;
    private Map<String, String> dbCoordinates = new HashMap<>();
    private String status = "SUCCESS";
    private String errorMessage;

    public BackupManifest() {
    }

    public String getBackupId() {
        return backupId;
    }

    public void setBackupId(String backupId) {
        this.backupId = backupId;
    }

    public String getParentBackupId() {
        return parentBackupId;
    }

    public void setParentBackupId(String parentBackupId) {
        this.parentBackupId = parentBackupId;
    }

    public BackupType getBackupType() {
        return backupType;
    }

    public void setBackupType(BackupType backupType) {
        this.backupType = backupType;
    }

    public DatabaseType getDatabaseType() {
        return databaseType;
    }

    public void setDatabaseType(DatabaseType databaseType) {
        this.databaseType = databaseType;
    }

    public String getDatabaseName() {
        return databaseName;
    }

    public void setDatabaseName(String databaseName) {
        this.databaseName = databaseName;
    }

    public String getServerHost() {
        return serverHost;
    }

    public void setServerHost(String serverHost) {
        this.serverHost = serverHost;
    }

    public int getServerPort() {
        return serverPort;
    }

    public void setServerPort(int serverPort) {
        this.serverPort = serverPort;
    }

    public Instant getStartTime() {
        return startTime;
    }

    public void setStartTime(Instant startTime) {
        this.startTime = startTime;
    }

    public Instant getEndTime() {
        return endTime;
    }

    public void setEndTime(Instant endTime) {
        this.endTime = endTime;
    }

    public long getDurationMillis() {
        return durationMillis;
    }

    public void setDurationMillis(long durationMillis) {
        this.durationMillis = durationMillis;
    }

    public long getRawBytes() {
        return rawBytes;
    }

    public void setRawBytes(long rawBytes) {
        this.rawBytes = rawBytes;
    }

    public long getArchiveBytes() {
        return archiveBytes;
    }

    public void setArchiveBytes(long archiveBytes) {
        this.archiveBytes = archiveBytes;
    }

    public String getSha256Checksum() {
        return sha256Checksum;
    }

    public void setSha256Checksum(String sha256Checksum) {
        this.sha256Checksum = sha256Checksum;
    }

    public CompressionType getCompression() {
        return compression;
    }

    public void setCompression(CompressionType compression) {
        this.compression = compression;
    }

    public String getEncryptionAlgorithm() {
        return encryptionAlgorithm;
    }

    public void setEncryptionAlgorithm(String encryptionAlgorithm) {
        this.encryptionAlgorithm = encryptionAlgorithm;
    }

    public String getStorageLocation() {
        return storageLocation;
    }

    public void setStorageLocation(String storageLocation) {
        this.storageLocation = storageLocation;
    }

    public List<String> getIncludedTables() {
        return includedTables;
    }

    public void setIncludedTables(List<String> includedTables) {
        this.includedTables = includedTables;
    }

    public Map<String, String> getDbCoordinates() {
        return dbCoordinates;
    }

    public void setDbCoordinates(Map<String, String> dbCoordinates) {
        this.dbCoordinates = dbCoordinates;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public double getCompressionRatio() {
        if (rawBytes == 0 || archiveBytes == 0) return 1.0;
        return (double) rawBytes / (double) archiveBytes;
    }
}

