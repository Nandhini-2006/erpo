package com.datashield.storage;

import com.datashield.core.model.BackupManifest;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

/**
 * Enterprise storage provider SPI for local filesystems and cloud object storage.
 */
public interface StorageProvider {
    /**
     * Obtains an OutputStream to write the backup archive into storage.
     */
    OutputStream openOutputStream(String archiveName) throws IOException;

    /**
     * Obtains an InputStream to read an existing backup archive from storage.
     */
    InputStream openInputStream(String archiveName) throws IOException;

    /**
     * Writes the backup manifest metadata to storage.
     */
    void saveManifest(BackupManifest manifest) throws IOException;

    /**
     * Reads the backup manifest metadata from storage.
     */
    BackupManifest loadManifest(String backupId) throws IOException;

    /**
     * Lists all backup archives present in this storage target.
     */
    List<BackupManifest> listBackups() throws IOException;

    /**
     * Deletes a backup archive and its associated manifest.
     */
    boolean deleteBackup(String backupId) throws IOException;

    /**
     * Verifies connectivity, permissions, and target availability.
     */
    void testConnectivity() throws IOException;

    /**
     * Returns human-readable storage location description.
     */
    String getLocationDescription();
}

