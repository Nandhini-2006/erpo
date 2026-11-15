package com.datashield.adapter.postgres;

import com.datashield.adapter.DatabaseAdapter;
import com.datashield.adapter.jdbc.JdbcDumpHelper;
import com.datashield.core.model.BackupRequest;
import com.datashield.core.model.ConnectionConfig;
import com.datashield.core.model.DatabaseType;
import com.datashield.core.model.RestoreRequest;
import com.datashield.util.ProcessRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.sql.*;
import java.util.*;

public class PostgreSqlAdapter implements DatabaseAdapter {
    private static final Logger log = LoggerFactory.getLogger(PostgreSqlAdapter.class);

    @Override
    public DatabaseType getType() {
        return DatabaseType.POSTGRESQL;
    }

    @Override
    public void testConnection(ConnectionConfig config) throws Exception {
        log.info("Testing PostgreSQL connection to {}:{}", config.getHost(), config.getPort());
        try (Connection conn = DriverManager.getConnection(config.getJdbcUrl(), config.getUsername(), config.getPassword());
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT version()")) {
            if (rs.next()) {
                log.info("Successfully connected to PostgreSQL. Version: {}", rs.getString(1));
            }
        }
    }

    @Override
    public void streamBackup(ConnectionConfig config, BackupRequest request, OutputStream targetStream, Map<String, String> coordinatesOut) throws Exception {
        try {
            coordinatesOut.putAll(captureCoordinates(config));
        } catch (Exception e) {
            log.warn("Could not capture PostgreSQL WAL coordinates: {}", e.getMessage());
        }

        if (ProcessRunner.isCommandAvailable("pg_dump")) {
            log.info("Executing native pg_dump stream...");
            streamNativePgDump(config, request, targetStream);
        } else {
            log.info("Native pg_dump not detected on PATH. Using high-performance Pure-JDBC streaming dump engine.");
            try (Connection conn = DriverManager.getConnection(config.getJdbcUrl(), config.getUsername(), config.getPassword())) {
                JdbcDumpHelper.dumpDatabase(conn, request.toTableFilter(), targetStream, config.getDatabase());
            }
        }
    }

    private void streamNativePgDump(ConnectionConfig config, BackupRequest request, OutputStream targetStream) throws Exception {
        List<String> cmd = new ArrayList<>();
        cmd.add("pg_dump");
        cmd.add("-h"); cmd.add(config.getHost());
        cmd.add("-p"); cmd.add(String.valueOf(config.getPort()));
        if (config.getUsername() != null && !config.getUsername().isBlank()) {
            cmd.add("-U"); cmd.add(config.getUsername());
        }
        cmd.add("-d"); cmd.add(config.getDatabase());
        cmd.add("--no-owner");
        cmd.add("--no-acl");

        for (String table : request.getIncludeTables()) {
            cmd.add("-t"); cmd.add(table);
        }
        for (String exclude : request.getExcludeTables()) {
            cmd.add("-T"); cmd.add(exclude);
        }

        Map<String, String> env = new HashMap<>();
        if (config.getPassword() != null && !config.getPassword().isBlank()) {
            env.put("PGPASSWORD", config.getPassword());
        }

        Process process = ProcessRunner.startProcess(cmd, env);
        String stderr = ProcessRunner.captureStderr(process);

        try (InputStream in = new BufferedInputStream(process.getInputStream())) {
            in.transferTo(targetStream);
        }

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new IOException("pg_dump failed with exit code " + exitCode + ": " + stderr);
        }
    }

    @Override
    public void streamRestore(ConnectionConfig config, RestoreRequest request, InputStream sourceStream) throws Exception {
        if (ProcessRunner.isCommandAvailable("psql")) {
            log.info("Executing native psql restore stream...");
            List<String> cmd = new ArrayList<>();
            cmd.add("psql");
            cmd.add("-h"); cmd.add(config.getHost());
            cmd.add("-p"); cmd.add(String.valueOf(config.getPort()));
            if (config.getUsername() != null && !config.getUsername().isBlank()) {
                cmd.add("-U"); cmd.add(config.getUsername());
            }
            cmd.add("-d"); cmd.add(config.getDatabase());

            Map<String, String> env = new HashMap<>();
            if (config.getPassword() != null && !config.getPassword().isBlank()) {
                env.put("PGPASSWORD", config.getPassword());
            }

            Process process = ProcessRunner.startProcess(cmd, env);
            String stderr = ProcessRunner.captureStderr(process);

            try (OutputStream out = new BufferedOutputStream(process.getOutputStream())) {
                sourceStream.transferTo(out);
            }

            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new IOException("psql restore failed with exit code " + exitCode + ": " + stderr);
            }
        } else {
            log.info("Native psql CLI not detected on PATH. Restoring via Pure-JDBC SQL streaming execution.");
            try (Connection conn = DriverManager.getConnection(config.getJdbcUrl(), config.getUsername(), config.getPassword())) {
                JdbcDumpHelper.restoreSqlStream(conn, sourceStream, request.toTableFilter());
            }
        }
    }

    @Override
    public Map<String, String> captureCoordinates(ConnectionConfig config) throws Exception {
        Map<String, String> coords = new HashMap<>();
        try (Connection conn = DriverManager.getConnection(config.getJdbcUrl(), config.getUsername(), config.getPassword());
             Statement stmt = conn.createStatement()) {
            ResultSet rs = null;
            try {
                rs = stmt.executeQuery("SELECT pg_current_wal_lsn()::text");
            } catch (SQLException e) {
                rs = stmt.executeQuery("SELECT pg_current_xlog_location()::text");
            }
            if (rs.next()) {
                coords.put("wal_lsn", rs.getString(1));
            }
        }
        return coords;
    }

    @Override
    public List<String> listTablesOrCollections(ConnectionConfig config) throws Exception {
        try (Connection conn = DriverManager.getConnection(config.getJdbcUrl(), config.getUsername(), config.getPassword())) {
            return JdbcDumpHelper.getTables(conn, "public");
        }
    }

    @Override
    public boolean supportsIncremental() {
        return true;
    }
}

