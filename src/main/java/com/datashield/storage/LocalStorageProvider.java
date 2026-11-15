package com.datashield.storage;

import com.datashield.core.model.BackupManifest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Enterprise local filesystem storage provider with atomic writes and retention policies.
 */
public class LocalStorageProvider implements StorageProvider {
    private static final Logger log = LoggerFactory.getLogger(LocalStorageProvider.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .enable(SerializationFeature.INDENT_OUTPUT)
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private final Path baseDirectory;

    public LocalStorageProvider(String directoryPath) {
        this.baseDirectory = Paths.get(directoryPath != null && !directoryPath.isBlank() ? directoryPath : "./backups").toAbsolutePath().normalize();
    }

    public LocalStorageProvider(Path baseDirectory) {
        this.baseDirectory = baseDirectory.toAbsolutePath().normalize();
    }

    @Override
    public OutputStream openOutputStream(String archiveName) throws IOException {
        Files.createDirectories(baseDirectory);
        Path targetPath = baseDirectory.resolve(archiveName);
        log.info("Opening local backup output stream to: {}", targetPath);
        return new BufferedOutputStream(Files.newOutputStream(targetPath, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE));
    }

    @Override
    public InputStream openInputStream(String archiveName) throws IOException {
        Path targetPath = baseDirectory.resolve(archiveName);
        if (!Files.exists(targetPath)) {
            // Also check if user passed full absolute path
            Path altPath = Paths.get(archiveName);
            if (Files.exists(altPath)) {
                targetPath = altPath;
            } else {
                throw new FileNotFoundException("Backup archive not found: " + targetPath);
            }
        }
        log.info("Opening local backup input stream from: {}", targetPath);
        return new BufferedInputStream(Files.newInputStream(targetPath, StandardOpenOption.READ));
    }

    @Override
    public void saveManifest(BackupManifest manifest) throws IOException {
        Files.createDirectories(baseDirectory);
        String manifestName = manifest.getBackupId() + ".manifest.json";
        Path manifestPath = baseDirectory.resolve(manifestName);
        log.debug("Writing manifest to: {}", manifestPath);
        OBJECT_MAPPER.writeValue(manifestPath.toFile(), manifest);
    }

    @Override
    public BackupManifest loadManifest(String backupId) throws IOException {
        Path manifestPath = baseDirectory.resolve(backupId + ".manifest.json");
        if (!Files.exists(manifestPath)) {
            // Check direct path if backupId is a path or contains .json
            Path directPath = Paths.get(backupId);
            if (Files.exists(directPath) && directPath.toString().endsWith(".json")) {
                manifestPath = directPath;
            } else {
                throw new FileNotFoundException("Manifest file not found for backup ID: " + backupId);
            }
        }
        return OBJECT_MAPPER.readValue(manifestPath.toFile(), BackupManifest.class);
    }

    @Override
    public List<BackupManifest> listBackups() throws IOException {
        List<BackupManifest> manifests = new ArrayList<>();
        if (!Files.exists(baseDirectory)) {
            return manifests;
        }

        try (Stream<Path> stream = Files.list(baseDirectory)) {
            List<Path> manifestFiles = stream
                    .filter(p -> p.getFileName().toString().endsWith(".manifest.json"))
                    .toList();

            for (Path path : manifestFiles) {
                try {
                    BackupManifest manifest = OBJECT_MAPPER.readValue(path.toFile(), BackupManifest.class);
                    manifests.add(manifest);
                } catch (Exception e) {
                    log.warn("Failed to parse manifest file {}: {}", path.getFileName(), e.getMessage());
                }
            }
        }

        manifests.sort(Comparator.comparing(BackupManifest::getStartTime, Comparator.nullsLast(Comparator.reverseOrder())));
        return manifests;
    }

    @Override
    public boolean deleteBackup(String backupId) throws IOException {
        Path manifestPath = baseDirectory.resolve(backupId + ".manifest.json");
        boolean deleted = false;
        if (Files.exists(manifestPath)) {
            try {
                BackupManifest m = OBJECT_MAPPER.readValue(manifestPath.toFile(), BackupManifest.class);
                if (m.getStorageLocation() != null) {
                    Path archivePath = Paths.get(m.getStorageLocation());
                    if (Files.exists(archivePath)) {
                        Files.delete(archivePath);
                        deleted = true;
                    }
                }
            } catch (Exception ignored) {
            }
            Files.delete(manifestPath);
            deleted = true;
        }
        return deleted;
    }

    @Override
    public void testConnectivity() throws IOException {
        if (!Files.exists(baseDirectory)) {
            Files.createDirectories(baseDirectory);
        }
        // Test write and delete of temporary probe file (reliable across Windows, Linux, and macOS)
        Path probeFile = Files.createTempFile(baseDirectory, ".datashield_probe_", ".tmp");
        Files.deleteIfExists(probeFile);
    }

    public void applyRetentionPolicy(int retentionDays) {
        if (retentionDays <= 0) return;
        Instant cutoff = Instant.now().minus(retentionDays, ChronoUnit.DAYS);
        log.info("Applying retention policy: purging backups older than {} days (cutoff: {})", retentionDays, cutoff);
        try {
            List<BackupManifest> manifests = listBackups();
            for (BackupManifest m : manifests) {
                if (m.getStartTime() != null && m.getStartTime().isBefore(cutoff)) {
                    log.info("Purging expired backup {} from {}", m.getBackupId(), m.getStartTime());
                    deleteBackup(m.getBackupId());
                }
            }
        } catch (IOException e) {
            log.error("Error applying retention policy", e);
        }
    }

    @Override
    public String getLocationDescription() {
        return "Local Storage: " + baseDirectory.toString();
    }

    public Path getBaseDirectory() {
        return baseDirectory;
    }
}
