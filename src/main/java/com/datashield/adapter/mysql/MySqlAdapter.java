package com.datashield.adapter.mysql;

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

public class MySqlAdapter implements DatabaseAdapter {
    private static final Logger log = LoggerFactory.getLogger(MySqlAdapter.class);

    @Override
    public DatabaseType getType() {
        return DatabaseType.MYSQL;
    }

    @Override
    public void testConnection(ConnectionConfig config) throws Exception {
        log.info("Testing MySQL connection to {}:{}", config.getHost(), config.getPort());
        try (Connection conn = DriverManager.getConnection(config.getJdbcUrl(), config.getUsername(), config.getPassword());
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT VERSION()")) {
            if (rs.next()) {
                log.info("Successfully connected to MySQL. Server version: {}", rs.getString(1));
            }
        }
    }

    @Override
    public void streamBackup(ConnectionConfig config, BackupRequest request, OutputStream targetStream, Map<String, String> coordinatesOut) throws Exception {
        // Capture binlog coordinates if possible
        try {
            coordinatesOut.putAll(captureCoordinates(config));
        } catch (Exception e) {
            log.warn("Could not capture MySQL binlog coordinates (may lack SUPER/REPLICATION privileges): {}", e.getMessage());
        }

        if (ProcessRunner.isCommandAvailable("mysqldump")) {
            log.info("Executing native mysqldump stream...");
            streamNativeMysqldump(config, request, targetStream);
        } else {
            log.info("Native mysqldump not detected on PATH. Using high-performance Pure-JDBC streaming dump engine.");
            try (Connection conn = DriverManager.getConnection(config.getJdbcUrl(), config.getUsername(), config.getPassword())) {
                JdbcDumpHelper.dumpDatabase(conn, request.toTableFilter(), targetStream, config.getDatabase());
            }
        }
    }

    private void streamNativeMysqldump(ConnectionConfig config, BackupRequest request, OutputStream targetStream) throws Exception {
        List<String> cmd = new ArrayList<>();
        cmd.add("mysqldump");
        cmd.add("-h"); cmd.add(config.getHost());
        cmd.add("-P"); cmd.add(String.valueOf(config.getPort()));
        if (config.getUsername() != null && !config.getUsername().isBlank()) {
            cmd.add("-u"); cmd.add(config.getUsername());
        }
        cmd.add("--single-transaction");
        cmd.add("--quick");
        cmd.add("--skip-lock-tables");
        cmd.add("--routines");
        cmd.add("--triggers");
        cmd.add("--events");
        cmd.add(config.getDatabase());

        if (!request.getIncludeTables().isEmpty()) {
            cmd.addAll(request.getIncludeTables());
        }

        Map<String, String> env = new HashMap<>();
        if (config.getPassword() != null && !config.getPassword().isBlank()) {
            env.put("MYSQL_PWD", config.getPassword());
        }

        Process process = ProcessRunner.startProcess(cmd, env);
        String stderr = ProcessRunner.captureStderr(process);

        try (InputStream in = new BufferedInputStream(process.getInputStream())) {
            in.transferTo(targetStream);
        }

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new IOException("mysqldump failed with exit code " + exitCode + ": " + stderr);
        }
    }

    @Override
    public void streamRestore(ConnectionConfig config, RestoreRequest request, InputStream sourceStream) throws Exception {
        if (ProcessRunner.isCommandAvailable("mysql")) {
            log.info("Executing native mysql restore stream...");
            List<String> cmd = new ArrayList<>();
            cmd.add("mysql");
            cmd.add("-h"); cmd.add(config.getHost());
            cmd.add("-P"); cmd.add(String.valueOf(config.getPort()));
            if (config.getUsername() != null && !config.getUsername().isBlank()) {
                cmd.add("-u"); cmd.add(config.getUsername());
            }
            cmd.add(config.getDatabase());

            Map<String, String> env = new HashMap<>();
            if (config.getPassword() != null && !config.getPassword().isBlank()) {
                env.put("MYSQL_PWD", config.getPassword());
            }

            Process process = ProcessRunner.startProcess(cmd, env);
            String stderr = ProcessRunner.captureStderr(process);

            try (OutputStream out = new BufferedOutputStream(process.getOutputStream())) {
                sourceStream.transferTo(out);
            }

            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new IOException("mysql restore failed with exit code " + exitCode + ": " + stderr);
            }
        } else {
            log.info("Native mysql CLI not detected on PATH. Restoring via Pure-JDBC SQL streaming execution.");
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
            // MySQL 8.4+ uses SHOW BINARY LOG STATUS, older uses SHOW MASTER STATUS
            ResultSet rs = null;
            try {
                rs = stmt.executeQuery("SHOW BINARY LOG STATUS");
            } catch (SQLException e) {
                rs = stmt.executeQuery("SHOW MASTER STATUS");
            }
            if (rs.next()) {
                coords.put("binlog_file", rs.getString(1));
                coords.put("binlog_position", String.valueOf(rs.getLong(2)));
            }
        }
        return coords;
    }

    @Override
    public List<String> listTablesOrCollections(ConnectionConfig config) throws Exception {
        try (Connection conn = DriverManager.getConnection(config.getJdbcUrl(), config.getUsername(), config.getPassword())) {
            return JdbcDumpHelper.getTables(conn, config.getDatabase());
        }
    }

    @Override
    public boolean supportsIncremental() {
        return true;
    }
}

