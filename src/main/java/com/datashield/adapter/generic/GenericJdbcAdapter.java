package com.datashield.adapter.generic;

import com.datashield.adapter.DatabaseAdapter;
import com.datashield.adapter.jdbc.JdbcDumpHelper;
import com.datashield.core.model.BackupRequest;
import com.datashield.core.model.ConnectionConfig;
import com.datashield.core.model.DatabaseType;
import com.datashield.core.model.RestoreRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.io.OutputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class GenericJdbcAdapter implements DatabaseAdapter {
    private static final Logger log = LoggerFactory.getLogger(GenericJdbcAdapter.class);

    @Override
    public DatabaseType getType() {
        return DatabaseType.GENERIC_JDBC;
    }

    @Override
    public void testConnection(ConnectionConfig config) throws Exception {
        log.info("Testing Generic JDBC connection to: {}", config.getJdbcUrl());
        try (Connection conn = DriverManager.getConnection(config.getJdbcUrl(), config.getUsername(), config.getPassword());
             Statement stmt = conn.createStatement()) {
            stmt.execute("SELECT 1");
            log.info("Generic JDBC connection test succeeded.");
        }
    }

    @Override
    public void streamBackup(ConnectionConfig config, BackupRequest request, OutputStream targetStream, Map<String, String> coordinatesOut) throws Exception {
        log.info("Executing Generic JDBC streaming backup...");
        try (Connection conn = DriverManager.getConnection(config.getJdbcUrl(), config.getUsername(), config.getPassword())) {
            JdbcDumpHelper.dumpDatabase(conn, request.toTableFilter(), targetStream, config.getDatabase());
        }
    }

    @Override
    public void streamRestore(ConnectionConfig config, RestoreRequest request, InputStream sourceStream) throws Exception {
        log.info("Executing Generic JDBC streaming restore...");
        try (Connection conn = DriverManager.getConnection(config.getJdbcUrl(), config.getUsername(), config.getPassword())) {
            JdbcDumpHelper.restoreSqlStream(conn, sourceStream, request.toTableFilter());
        }
    }

    @Override
    public Map<String, String> captureCoordinates(ConnectionConfig config) {
        return Collections.emptyMap();
    }

    @Override
    public List<String> listTablesOrCollections(ConnectionConfig config) throws Exception {
        try (Connection conn = DriverManager.getConnection(config.getJdbcUrl(), config.getUsername(), config.getPassword())) {
            return JdbcDumpHelper.getTables(conn, config.getDatabase());
        }
    }

    @Override
    public boolean supportsIncremental() {
        return false;
    }
}

