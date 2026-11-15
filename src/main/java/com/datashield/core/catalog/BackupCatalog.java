package com.datashield.core.catalog;

import com.datashield.core.model.BackupManifest;
import com.datashield.core.model.BackupType;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

public interface BackupCatalog {
    void register(BackupManifest manifest) throws IOException;
    Optional<BackupManifest> getLatestBackup(String databaseName, BackupType type);
    Optional<BackupManifest> getBackup(String backupId);
    List<BackupManifest> listAll();
    List<BackupManifest> findByDatabase(String databaseName);
    boolean delete(String backupId) throws IOException;
    List<BackupManifest> getLineageChain(String backupId);
}

