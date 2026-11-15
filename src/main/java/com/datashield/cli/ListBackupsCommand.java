package com.datashield.cli;

import com.datashield.core.catalog.BackupCatalog;
import com.datashield.core.catalog.JsonFileBackupCatalog;
import com.datashield.core.model.BackupManifest;
import com.datashield.storage.StorageProvider;
import com.datashield.storage.StorageProviderFactory;
import com.datashield.util.ByteUtils;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.util.List;
import java.util.concurrent.Callable;

@Command(name = "list", description = "List registered backups and lineage from catalog or storage destination", mixinStandardHelpOptions = true)
public class ListBackupsCommand implements Callable<Integer> {

    @Option(names = {"--storage"}, description = "Target storage to inspect directly (e.g. ./backups or s3://my-bucket/path)")
    private String storageLocation;

    @Option(names = {"-D", "--database"}, description = "Filter by database name")
    private String database;

    @Override
    public Integer call() {
        try {
            List<BackupManifest> backups;
            if (storageLocation != null && !storageLocation.isBlank()) {
                StorageProvider storage = StorageProviderFactory.create(storageLocation);
                backups = storage.listBackups();
            } else {
                BackupCatalog catalog = new JsonFileBackupCatalog();
                backups = database != null ? catalog.findByDatabase(database) : catalog.listAll();
            }

            if (backups.isEmpty()) {
                System.out.println("No backups found.");
                return 0;
            }

            System.out.println("=========================================================================================================================");
            System.out.printf("%-10s | %-12s | %-10s | %-12s | %-20s | %-10s | %-10s | %-6s | %-10s%n",
                    "BACKUP ID", "DATABASE", "DBMS", "TYPE", "START TIME (UTC)", "RAW SIZE", "ARCHIVE", "RATIO", "PARENT ID");
            System.out.println("=========================================================================================================================");

            for (BackupManifest m : backups) {
                String shortId = m.getBackupId() != null && m.getBackupId().length() > 8 ? m.getBackupId().substring(0, 8) : m.getBackupId();
                String parentId = m.getParentBackupId() != null && m.getParentBackupId().length() > 8 ? m.getParentBackupId().substring(0, 8) : (m.getParentBackupId() != null ? m.getParentBackupId() : "-");
                String startTime = m.getStartTime() != null ? m.getStartTime().toString().replace("T", " ").substring(0, 19) : "-";

                System.out.printf("%-10s | %-12s | %-10s | %-12s | %-20s | %-10s | %-10s | %-6.2fx | %-10s%n",
                        shortId,
                        truncate(m.getDatabaseName(), 12),
                        m.getDatabaseType(),
                        m.getBackupType(),
                        startTime,
                        ByteUtils.humanReadableByteCount(m.getRawBytes()),
                        ByteUtils.humanReadableByteCount(m.getArchiveBytes()),
                        m.getCompressionRatio(),
                        parentId);
            }
            System.out.println("=========================================================================================================================");
            System.out.printf("Total Backups: %d%n", backups.size());
            return 0;
        } catch (Exception e) {
            System.err.println("Failed to list backups: " + e.getMessage());
            e.printStackTrace(System.err);
            return 1;
        }
    }

    private String truncate(String s, int max) {
        if (s == null) return "-";
        return s.length() > max ? s.substring(0, max - 2) + ".." : s;
    }
}

