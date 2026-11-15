package com.datashield.core.catalog;

import com.datashield.core.model.BackupManifest;
import com.datashield.core.model.BackupType;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Enterprise local JSON file catalog maintaining global metadata and lineage graphs.
 */
public class JsonFileBackupCatalog implements BackupCatalog {
    private static final Logger log = LoggerFactory.getLogger(JsonFileBackupCatalog.class);
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .enable(SerializationFeature.INDENT_OUTPUT);

    private final Path catalogPath;
    private final Map<String, BackupManifest> entries = new ConcurrentHashMap<>();

    public JsonFileBackupCatalog() {
        this(resolveDefaultCatalogPath());
    }

    private static Path resolveDefaultCatalogPath() {
        Path localDir = Paths.get(".datashield");
        Path homeDir = Paths.get(System.getProperty("user.home"), ".datashield");

        try {
            if (!Files.exists(homeDir)) {
                Files.createDirectories(homeDir);
            }
            return homeDir.resolve("catalog.json");
        } catch (Exception ignored) {
            return localDir.resolve("catalog.json");
        }
    }

    public JsonFileBackupCatalog(Path catalogPath) {
        this.catalogPath = catalogPath.toAbsolutePath().normalize();
        load();
    }

    private synchronized void load() {
        if (!Files.exists(catalogPath)) {
            return;
        }
        try {
            List<BackupManifest> list = MAPPER.readValue(catalogPath.toFile(), new TypeReference<List<BackupManifest>>() {});
            entries.clear();
            for (BackupManifest m : list) {
                if (m.getBackupId() != null) {
                    entries.put(m.getBackupId(), m);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to read catalog file {}: {}", catalogPath, e.getMessage());
        }
    }

    private synchronized void persist() throws IOException {
        Path target = catalogPath;
        try {
            if (target.getParent() != null) {
                Files.createDirectories(target.getParent());
            }
        } catch (Exception e) {
            target = Paths.get(".datashield", "catalog.json");
            Files.createDirectories(target.getParent());
        }
        List<BackupManifest> list = new ArrayList<>(entries.values());
        list.sort(Comparator.comparing(BackupManifest::getStartTime, Comparator.nullsLast(Comparator.reverseOrder())));
        MAPPER.writeValue(target.toFile(), list);
    }

    @Override
    public synchronized void register(BackupManifest manifest) throws IOException {
        if (manifest != null && manifest.getBackupId() != null) {
            entries.put(manifest.getBackupId(), manifest);
            persist();
        }
    }

    @Override
    public Optional<BackupManifest> getLatestBackup(String databaseName, BackupType type) {
        return entries.values().stream()
                .filter(m -> databaseName == null || databaseName.equalsIgnoreCase(m.getDatabaseName()))
                .filter(m -> type == null || m.getBackupType() == type)
                .filter(m -> "SUCCESS".equalsIgnoreCase(m.getStatus()))
                .max(Comparator.comparing(BackupManifest::getStartTime, Comparator.nullsLast(Comparator.naturalOrder())));
    }

    @Override
    public Optional<BackupManifest> getBackup(String backupId) {
        if (backupId == null) return Optional.empty();
        return Optional.ofNullable(entries.get(backupId));
    }

    @Override
    public List<BackupManifest> listAll() {
        List<BackupManifest> list = new ArrayList<>(entries.values());
        list.sort(Comparator.comparing(BackupManifest::getStartTime, Comparator.nullsLast(Comparator.reverseOrder())));
        return list;
    }

    @Override
    public List<BackupManifest> findByDatabase(String databaseName) {
        return entries.values().stream()
                .filter(m -> databaseName == null || databaseName.equalsIgnoreCase(m.getDatabaseName()))
                .sorted(Comparator.comparing(BackupManifest::getStartTime, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

       @Override
    public synchronized boolean delete(String backupId) throws IOException {
        if (entries.remove(backupId) != null) {
            persist();
            return true;
        }
        return false;
    }

    @Override
    public List<BackupManifest> getLineageChain(String backupId) {
        List<BackupManifest> chain = new ArrayList<>();
        String currentId = backupId;
        while (currentId != null) {
            BackupManifest m = entries.get(currentId);
            if (m == null) break;
            chain.add(0, m);
            if (m.getBackupType() == BackupType.FULL) {
                break;
            }
            currentId = m.getParentBackupId();
        }
        return chain;
    }

}
