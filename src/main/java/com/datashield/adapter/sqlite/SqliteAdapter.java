package com.datashield.adapter.sqlite;

import com.datashield.adapter.DatabaseAdapter;
import com.datashield.adapter.jdbc.JdbcDumpHelper;
import com.datashield.core.model.BackupRequest;
import com.datashield.core.model.ConnectionConfig;
import com.datashield.core.model.DatabaseType;
import com.datashield.core.model.RestoreRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.sql.*;
import java.util.*;

public class SqliteAdapter implements DatabaseAdapter {
    private static final Logger log = LoggerFactory.getLogger(SqliteAdapter.class);

    @Override
    public DatabaseType getType() {
        return DatabaseType.SQLITE;
    }

    @Override
    public void testConnection(ConnectionConfig config) throws Exception {
        String dbPath = resolveDbPath(config);
        log.info("Testing SQLite connection to: {}", dbPath);
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("PRAGMA schema_version")) {
            if (rs.next()) {
                log.info("Successfully connected to SQLite database. Schema version: {}", rs.getInt(1));
            }
        }
    }

    @Override
    public void streamBackup(ConnectionConfig config, BackupRequest request, OutputStream targetStream, Map<String, String> coordinatesOut) throws Exception {
        String dbPath = resolveDbPath(config);

        if (request.toTableFilter().hasFilters()) {
            // Selective tables dump using SQL script
            log.info("Performing selective SQLite table backup via JDBC dump helper...");
            try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath)) {
                JdbcDumpHelper.dumpDatabase(conn, request.toTableFilter(), targetStream, null);
            }
            return;
        }

        // Full backup: try online transactional VACUUM INTO snapshot
        Path tempSnapshot = Files.createTempFile("datashield_sqlite_snapshot_", ".db");
        try {
            Files.deleteIfExists(tempSnapshot); // VACUUM INTO requires target file to not exist
            log.info("Executing SQLite transactional VACUUM INTO snapshot: {}", tempSnapshot);
            try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
                 Statement stmt = conn.createStatement()) {
                stmt.execute("VACUUM INTO '" + tempSnapshot.toString().replace("\\", "/") + "'");
            } catch (SQLException e) {
                log.warn("VACUUM INTO failed ({}), falling back to direct stream copy", e.getMessage());
                Path sourcePath = Paths.get(dbPath);
                if (Files.exists(sourcePath)) {
                    Files.copy(sourcePath, tempSnapshot, StandardCopyOption.REPLACE_EXISTING);
                } else {
                    throw new FileNotFoundException("SQLite database file not found: " + dbPath);
                }
            }

            try (InputStream in = Files.newInputStream(tempSnapshot)) {
                in.transferTo(targetStream);
            }
        } finally {
            Files.deleteIfExists(tempSnapshot);
        }
    }

    @Override
    public void streamRestore(ConnectionConfig config, RestoreRequest request, InputStream sourceStream) throws Exception {
        String dbPath = resolveDbPath(config);
        Path targetPath = Paths.get(dbPath).toAbsolutePath();

        if (request.toTableFilter().hasFilters()) {
            log.info("Performing selective SQLite table restore via SQL streaming execution...");
            try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath)) {
                JdbcDumpHelper.restoreSqlStream(conn, sourceStream, request.toTableFilter());
            }
            return;
        }

        // Check if stream is SQLite binary file or SQL text
        PushbackInputStream pushback = new PushbackInputStream(sourceStream, 16);
        byte[] header = new byte[16];
        int read = pushback.read(header);
        pushback.unread(header, 0, read);

        String headerStr = new String(header);
        if (headerStr.startsWith("SQLite format 3")) {
            // Binary SQLite file
            log.info("Restoring binary SQLite database snapshot to: {}", targetPath);
            if (targetPath.getParent() != null) {
                Files.createDirectories(targetPath.getParent());
            }
            Files.copy(pushback, targetPath, StandardCopyOption.REPLACE_EXISTING);
        } else {
            // SQL dump script
            log.info("Restoring SQL script dump into SQLite database: {}", targetPath);
            try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath)) {
                JdbcDumpHelper.restoreSqlStream(conn, pushback, request.toTableFilter());
            }
        }
    }

    @Override
    public Map<String, String> captureCoordinates(ConnectionConfig config) throws Exception {
        Map<String, String> coords = new HashMap<>();
        String dbPath = resolveDbPath(config);
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("PRAGMA data_version")) {
            if (rs.next()) {
                coords.put("data_version", rs.getString(1));
            }
        }
        return coords;
    }

    @Override
    public List<String> listTablesOrCollections(ConnectionConfig config) throws Exception {
        String dbPath = resolveDbPath(config);
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath)) {
            return JdbcDumpHelper.getTables(conn, null);
        }
    }

    @Override
    public boolean supportsIncremental() {
        return false;
    }

    private String resolveDbPath(ConnectionConfig config) {
        if (config.getDatabase() != null && !config.getDatabase().isBlank()) {
            return config.getDatabase();
        }
        if (config.getHost() != null && !config.getHost().isBlank()) {
            return config.getHost();
        }
        return "database.db";
    }
}

