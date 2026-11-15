package com.datashield;

import com.datashield.core.catalog.BackupCatalog;
import com.datashield.core.catalog.JsonFileBackupCatalog;
import com.datashield.core.model.*;
import com.datashield.core.pipeline.BackupPipeline;
import com.datashield.core.pipeline.RestorePipeline;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class SqliteBackupRestoreIntegrationTest {

    @TempDir
    Path tempDir;

    private Path sourceDb;
    private Path targetDb;
    private Path backupDir;
    private BackupCatalog catalog;

    @BeforeEach
    void setUp() throws Exception {
        sourceDb = tempDir.resolve("source.db");
        targetDb = tempDir.resolve("target.db");
        backupDir = tempDir.resolve("backups");
        Files.createDirectories(backupDir);

        catalog = new JsonFileBackupCatalog(tempDir.resolve("catalog.json"));

        // Create test source database schema and populate records
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + sourceDb.toAbsolutePath());
             Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE users (id INTEGER PRIMARY KEY, name TEXT, email TEXT)");
            stmt.execute("CREATE TABLE orders (id INTEGER PRIMARY KEY, user_id INTEGER, amount REAL)");

            stmt.execute("INSERT INTO users VALUES (1, 'Alice Smith', 'alice@corp.com')");
            stmt.execute("INSERT INTO users VALUES (2, 'Bob Jones', 'bob@corp.com')");
            stmt.execute("INSERT INTO users VALUES (3, 'Charlie Brown', 'charlie@corp.com')");

            stmt.execute("INSERT INTO orders VALUES (101, 1, 499.95)");
            stmt.execute("INSERT INTO orders VALUES (102, 2, 1250.00)");
        }
    }

    @Test
    void shouldPerformFullEncryptedCompressedBackupAndRestore() throws Exception {
        String encryptionKey = "EnterpriseSecureKey#2026";

        // 1. Execute Backup
        BackupRequest backupReq = new BackupRequest();
        backupReq.getConnectionConfig().setDatabaseType(DatabaseType.SQLITE);
        backupReq.getConnectionConfig().setDatabase(sourceDb.toAbsolutePath().toString());
        backupReq.setBackupType(BackupType.FULL);
        backupReq.setCompression(CompressionType.GZIP);
        backupReq.setEncryptionPassword(encryptionKey);
        backupReq.setStorageDestination(backupDir.toAbsolutePath().toString());

        BackupPipeline backupPipeline = new BackupPipeline(catalog);
        BackupExecutionSummary backupSummary = backupPipeline.execute(backupReq);

        assertThat(backupSummary.isSuccess()).isTrue();
        assertThat(backupSummary.getRawBytes()).isGreaterThan(0);
        assertThat(backupSummary.getArchiveBytes()).isGreaterThan(0);
        assertThat(backupSummary.getSha256Checksum()).isNotNull();

        Path archiveFile = backupDir.resolve(backupSummary.getDestination());
        assertThat(Files.exists(archiveFile)).isTrue();

        // 2. Execute Restore into fresh target DB
        RestoreRequest restoreReq = new RestoreRequest();
        restoreReq.getTargetConnectionConfig().setDatabaseType(DatabaseType.SQLITE);
        restoreReq.getTargetConnectionConfig().setDatabase(targetDb.toAbsolutePath().toString());
        restoreReq.setBackupSource(archiveFile.toAbsolutePath().toString());
        restoreReq.setEncryptionPassword(encryptionKey);

        RestorePipeline restorePipeline = new RestorePipeline(catalog);
        BackupExecutionSummary restoreSummary = restorePipeline.execute(restoreReq);

        assertThat(restoreSummary.isSuccess()).isTrue();
        assertThat(Files.exists(targetDb)).isTrue();

        // 3. Verify restored data matches source
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + targetDb.toAbsolutePath());
             Statement stmt = conn.createStatement()) {
            try (ResultSet rs = stmt.executeQuery("SELECT count(*) FROM users")) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getInt(1)).isEqualTo(3);
            }
            try (ResultSet rs = stmt.executeQuery("SELECT name FROM users WHERE id = 1")) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getString(1)).isEqualTo("Alice Smith");
            }
            try (ResultSet rs = stmt.executeQuery("SELECT count(*) FROM orders")) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getInt(1)).isEqualTo(2);
            }
        }
    }

    @Test
    void shouldPerformSelectiveTableBackupAndRestore() throws Exception {
        Path selectiveTargetDb = tempDir.resolve("selective_target.db");

        // 1. Backup ONLY the 'users' table
        BackupRequest backupReq = new BackupRequest();
        backupReq.getConnectionConfig().setDatabaseType(DatabaseType.SQLITE);
        backupReq.getConnectionConfig().setDatabase(sourceDb.toAbsolutePath().toString());
        backupReq.setIncludeTables(Set.of("users"));
        backupReq.setCompression(CompressionType.GZIP);
        backupReq.setStorageDestination(backupDir.toAbsolutePath().toString());

        BackupPipeline backupPipeline = new BackupPipeline(catalog);
        BackupExecutionSummary backupSummary = backupPipeline.execute(backupReq);
        assertThat(backupSummary.isSuccess()).isTrue();

        Path archiveFile = backupDir.resolve(backupSummary.getDestination());

        // 2. Restore into selective target
        RestoreRequest restoreReq = new RestoreRequest();
        restoreReq.getTargetConnectionConfig().setDatabaseType(DatabaseType.SQLITE);
        restoreReq.getTargetConnectionConfig().setDatabase(selectiveTargetDb.toAbsolutePath().toString());
        restoreReq.setBackupSource(archiveFile.toAbsolutePath().toString());

        RestorePipeline restorePipeline = new RestorePipeline(catalog);
        BackupExecutionSummary restoreSummary = restorePipeline.execute(restoreReq);
        assertThat(restoreSummary.isSuccess()).isTrue();

        // 3. Verify 'users' exists, but 'orders' does NOT exist
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + selectiveTargetDb.toAbsolutePath());
             Statement stmt = conn.createStatement()) {
            try (ResultSet rs = stmt.executeQuery("SELECT count(*) FROM users")) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getInt(1)).isEqualTo(3);
            }
            try (ResultSet rs = stmt.executeQuery("SELECT count(*) FROM sqlite_master WHERE type='table' AND name='orders'")) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getInt(1)).isEqualTo(0);
            }
        }
    }
}

