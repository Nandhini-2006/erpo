package com.datashield.core.pipeline;

import com.datashield.adapter.DatabaseAdapter;
import com.datashield.adapter.DatabaseAdapterFactory;
import com.datashield.compression.CompressionProvider;
import com.datashield.compression.CompressionProviderFactory;
import com.datashield.core.catalog.BackupCatalog;
import com.datashield.core.model.*;
import com.datashield.crypto.AesGcmCryptoProvider;
import com.datashield.crypto.CryptoProvider;
import com.datashield.notify.NotificationService;
import com.datashield.notify.SlackNotifier;
import com.datashield.storage.LocalStorageProvider;
import com.datashield.storage.StorageProvider;
import com.datashield.storage.StorageProviderFactory;
import com.datashield.util.ByteUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

/**
 * Enterprise streaming backup pipeline engine orchestrating DB extraction,
 * real-time cryptographic hashing, streaming compression, AES encryption,
 * storage persistence, catalog registration, and Slack alerting.
 */
public class BackupPipeline {
    private static final Logger log = LoggerFactory.getLogger(BackupPipeline.class);

    private final BackupCatalog catalog;

    public BackupPipeline(BackupCatalog catalog) {
        this.catalog = catalog;
    }

    public BackupExecutionSummary execute(BackupRequest request) {
        String backupId = UUID.randomUUID().toString();
        Instant startTime = Instant.now();
        log.info("================================================================================");
        log.info("Starting DataShield Enterprise Backup [ID: {}]", backupId);
        log.info("Target DBMS: {} | Type: {} | Destination: {}",
                request.getConnectionConfig().getDatabaseType(),
                request.getBackupType(),
                request.getStorageDestination());
        log.info("================================================================================");

        DatabaseAdapter adapter = DatabaseAdapterFactory.getAdapter(request.getConnectionConfig().getDatabaseType());
        StorageProvider storage = StorageProviderFactory.create(request.getStorageDestination());
        CompressionProvider compression = CompressionProviderFactory.getProvider(request.getCompression());
        CryptoProvider crypto = request.getEncryptionPassword() != null && !request.getEncryptionPassword().isBlank()
                ? new AesGcmCryptoProvider() : null;

        NotificationService notifier = request.getSlackWebhookUrl() != null && !request.getSlackWebhookUrl().isBlank()
                ? new SlackNotifier(request.getSlackWebhookUrl()) : null;

        BackupManifest manifest = new BackupManifest();
        manifest.setBackupId(backupId);
        manifest.setBackupType(request.getBackupType());
        manifest.setDatabaseType(request.getConnectionConfig().getDatabaseType());
        manifest.setDatabaseName(request.getConnectionConfig().getDatabase());
        manifest.setServerHost(request.getConnectionConfig().getHost());
        manifest.setServerPort(request.getConnectionConfig().getPort());
        manifest.setStartTime(startTime);
        manifest.setCompression(request.getCompression());
        manifest.setEncryptionAlgorithm(crypto != null ? crypto.getAlgorithm() : "NONE");
        manifest.setStorageLocation(request.getStorageDestination());

        // Lineage lookup for Differential / Incremental
        if (request.getBackupType() != BackupType.FULL && catalog != null) {
            Optional<BackupManifest> parentOpt = catalog.getLatestBackup(
                    request.getConnectionConfig().getDatabase(),
                    request.getBackupType() == BackupType.DIFFERENTIAL ? BackupType.FULL : null);
            parentOpt.ifPresent(p -> manifest.setParentBackupId(p.getBackupId()));
        }

        if (request.isDryRun()) {
            log.info("DRY-RUN mode enabled: validating database connectivity and storage destination...");
            try {
                adapter.testConnection(request.getConnectionConfig());
                storage.testConnectivity();
                log.info("DRY-RUN completed successfully. All validations passed.");
                return new BackupExecutionSummary("BACKUP", backupId, true, startTime, Instant.now(),
                        0, 0, "dry-run-hash", request.getStorageDestination(), null);
            } catch (Exception e) {
                log.error("DRY-RUN validation failed: {}", e.getMessage(), e);
                return new BackupExecutionSummary("BACKUP", backupId, false, startTime, Instant.now(),
                        0, 0, null, request.getStorageDestination(), e.getMessage());
            }
        }

        // Test connectivity first
        try {
            adapter.testConnection(request.getConnectionConfig());
            storage.testConnectivity();
        } catch (Exception e) {
            String err = "Pre-flight connection validation failed: " + e.getMessage();
            log.error(err, e);
            BackupExecutionSummary failure = new BackupExecutionSummary("BACKUP", backupId, false,
                    startTime, Instant.now(), 0, 0, null, request.getStorageDestination(), err);
            if (notifier != null) notifier.notifyBackupCompleted(failure, manifest);
            return failure;
        }

        String archiveFileName = generateArchiveFileName(request, backupId);
        Map<String, String> dbCoords = new HashMap<>();

        long rawBytes = 0;
        long archiveBytes = 0;
        String checksum = null;

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            // Memory-efficient piped stream with 256 KB ring buffer
            PipedOutputStream pipedOut = new PipedOutputStream();
            PipedInputStream pipedIn = new PipedInputStream(pipedOut, 256 * 1024);

            // Task 1: Stream database extract into pipe
            Future<?> dbExportTask = executor.submit(() -> {
                try {
                    log.info("Database export worker thread started.");
                    adapter.streamBackup(request.getConnectionConfig(), request, pipedOut, dbCoords);
                    pipedOut.flush();
                } catch (Exception e) {
                    log.error("Error during database export: {}", e.getMessage(), e);
                    throw new RuntimeException("Database export failed: " + e.getMessage(), e);
                } finally {
                    try {
                        pipedOut.close();
                    } catch (IOException ignored) {
                    }
                }
            });

            // Task 2: Read piped data, compute raw hash/size, compress, encrypt, stream to storage
            Future<StreamingResult> storageTask = executor.submit(() -> {
                log.info("Storage pipeline processing thread started.");
                try (OutputStream storageOut = storage.openOutputStream(archiveFileName);
                     ProgressOutputStream progressOut = new ProgressOutputStream(storageOut)) {

                    OutputStream chain = progressOut;
                    if (crypto != null) {
                        log.info("Wrapping archive stream with AES-256-GCM encryption...");
                        chain = crypto.wrapEncryptor(chain, request.getEncryptionPassword());
                    }

                    log.info("Wrapping archive stream with {} compression...", request.getCompression());
                    chain = compression.wrapCompressor(chain);

                    // Compute raw uncompressed hash & size
                    HashingOutputStream hasher = new HashingOutputStream(chain);
                    byte[] buffer = new byte[64 * 1024];
                    int read;
                    while ((read = pipedIn.read(buffer)) != -1) {
                        hasher.write(buffer, 0, read);
                    }
                    hasher.flush();
                    hasher.close();

                    return new StreamingResult(hasher.getTotalBytes(), progressOut.getBytesWritten(), hasher.getSha256Checksum());
                } finally {
                    try {
                        pipedIn.close();
                    } catch (IOException ignored) {
                    }
                }
            });

            dbExportTask.get();
            StreamingResult result = storageTask.get();

            rawBytes = result.rawBytes;
            archiveBytes = result.archiveBytes;
            checksum = result.sha256;

            Instant endTime = Instant.now();
            long durationMillis = java.time.Duration.between(startTime, endTime).toMillis();

            manifest.setEndTime(endTime);
            manifest.setDurationMillis(durationMillis);
            manifest.setRawBytes(rawBytes);
            manifest.setArchiveBytes(archiveBytes);
            manifest.setSha256Checksum(checksum);
            manifest.setDbCoordinates(dbCoords);
            manifest.setStatus("SUCCESS");

            // Save manifest to storage destination and global catalog
            storage.saveManifest(manifest);
            if (catalog != null) {
                catalog.register(manifest);
            }

            // Retention cleanup if local storage
            if (storage instanceof LocalStorageProvider local && request.getRetentionDays() > 0) {
                local.applyRetentionPolicy(request.getRetentionDays());
            }

            BackupExecutionSummary summary = new BackupExecutionSummary("BACKUP", backupId, true,
                    startTime, endTime, rawBytes, archiveBytes, checksum, archiveFileName, null);

            log.info("================================================================================");
            log.info("DataShield Backup SUCCESS [ID: {}]", backupId);
            log.info("Archive File: {}", archiveFileName);
            log.info("Raw Size: {} | Archive Size: {} (Ratio: {:.2f}x)",
                    ByteUtils.humanReadableByteCount(rawBytes),
                    ByteUtils.humanReadableByteCount(archiveBytes),
                    manifest.getCompressionRatio());
            log.info("Throughput: {:.2f} MB/s | Elapsed: {}", summary.getThroughputMBps(), ByteUtils.formatDuration(durationMillis));
            log.info("SHA-256 Digest: {}", checksum);
            log.info("================================================================================");

            if (notifier != null) {
                notifier.notifyBackupCompleted(summary, manifest);
            }

            return summary;
        } catch (Exception e) {
            Throwable cause = e instanceof ExecutionException ? e.getCause() : e;
            String errMsg = cause != null ? cause.getMessage() : e.getMessage();
            log.error("Backup pipeline failed: {}", errMsg, e);

            manifest.setEndTime(Instant.now());
            manifest.setStatus("FAILED");
            manifest.setErrorMessage(errMsg);

            BackupExecutionSummary failure = new BackupExecutionSummary("BACKUP", backupId, false,
                    startTime, Instant.now(), rawBytes, archiveBytes, checksum, archiveFileName, errMsg);

            if (notifier != null) {
                notifier.notifyBackupCompleted(failure, manifest);
            }

            return failure;
        } finally {
            executor.shutdownNow();
        }
    }

    private String generateArchiveFileName(BackupRequest req, String backupId) {
        String db = req.getConnectionConfig().getDatabase() != null ? req.getConnectionConfig().getDatabase() : "db";
        String ext = req.getCompression().getExtension();
        if (req.getEncryptionPassword() != null && !req.getEncryptionPassword().isBlank()) {
            ext += ".enc";
        }
        return String.format("%s_%s_%s_%s%s",
                db,
                req.getBackupType().name().toLowerCase(),
                Instant.now().toString().replace(":", "-").replace(".", "-"),
                backupId.substring(0, 8),
                ext);
    }

    private record StreamingResult(long rawBytes, long archiveBytes, String sha256) {}
}

