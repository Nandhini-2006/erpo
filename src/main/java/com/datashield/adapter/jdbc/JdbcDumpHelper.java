package com.datashield.adapter.jdbc;

import com.datashield.core.model.TableFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Universal streaming JDBC backup and restore helper.
 * Extracts schema DDL and table data into standard SQL dump streams.
 */
public class JdbcDumpHelper {
    private static final Logger log = LoggerFactory.getLogger(JdbcDumpHelper.class);
    private static final int BATCH_SIZE = 500;

    public static List<String> getTables(Connection conn, String schemaOrCatalog) throws SQLException {
        List<String> tables = new ArrayList<>();
        DatabaseMetaData meta = conn.getMetaData();
        try (ResultSet rs = meta.getTables(schemaOrCatalog, null, "%", new String[]{"TABLE", "VIEW"})) {
            while (rs.next()) {
                String tableName = rs.getString("TABLE_NAME");
                if (tableName != null && !tableName.startsWith("pg_") && !tableName.startsWith("sql_")) {
                    tables.add(tableName);
                }
            }
        }
        return tables;
    }

    public static void dumpDatabase(Connection conn, TableFilter filter, OutputStream out, String dbName) throws IOException, SQLException {
        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8));

        writer.write("-- ========================================================\n");
        writer.write("-- DataShield Enterprise Database Backup Stream\n");
        writer.write("-- Database: " + (dbName != null ? dbName : "default") + "\n");
        writer.write("-- Exported: " + new java.util.Date() + "\n");
        writer.write("-- ========================================================\n\n");
        writer.write("SET FOREIGN_KEY_CHECKS = 0;\n\n");

        List<String> allTables = getTables(conn, dbName);
        List<String> targetTables = allTables.stream().filter(filter::shouldInclude).toList();

        log.info("Starting JDBC dump for {} target tables (filtered from total {})", targetTables.size(), allTables.size());

        for (String table : targetTables) {
            dumpTable(conn, table, writer);
        }

        writer.write("SET FOREIGN_KEY_CHECKS = 1;\n");
        writer.write("-- DataShield Backup Stream Completed --\n");
        writer.flush();
    }

    private static void dumpTable(Connection conn, String table, BufferedWriter writer) throws IOException, SQLException {
        log.debug("Dumping table: {}", table);
        writer.write("-- Table structure for: " + table + "\n");
        writer.write("DROP TABLE IF EXISTS " + escapeIdentifier(conn, table) + ";\n");

        // Attempt CREATE TABLE generation or extraction
        String createTableSql = extractCreateTable(conn, table);
        if (createTableSql != null) {
            writer.write(createTableSql + ";\n\n");
        }

        // Dump data
        writer.write("-- Data for table: " + table + "\n");
        String selectSql = "SELECT * FROM " + escapeIdentifier(conn, table);

        try (Statement stmt = conn.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY)) {
            stmt.setFetchSize(1000);
            try (ResultSet rs = stmt.executeQuery(selectSql)) {
                ResultSetMetaData meta = rs.getMetaData();
                int columnCount = meta.getColumnCount();

                List<String> rowValues = new ArrayList<>(BATCH_SIZE);
                long rowCount = 0;

                while (rs.next()) {
                    StringBuilder sb = new StringBuilder("(");
                    for (int i = 1; i <= columnCount; i++) {
                        if (i > 1) sb.append(", ");
                        Object val = rs.getObject(i);
                        if (val == null) {
                            sb.append("NULL");
                        } else if (val instanceof Number || val instanceof Boolean) {
                            sb.append(val);
                        } else if (val instanceof byte[] bytes) {
                            sb.append("X'").append(java.util.HexFormat.of().formatHex(bytes)).append("'");
                        } else {
                            sb.append("'").append(escapeSqlString(val.toString())).append("'");
                        }
                    }
                    sb.append(")");
                    rowValues.add(sb.toString());
                    rowCount++;

                    if (rowValues.size() >= BATCH_SIZE) {
                        flushInsertBatch(writer, conn, table, meta, rowValues);
                        rowValues.clear();
                    }
                }

                if (!rowValues.isEmpty()) {
                    flushInsertBatch(writer, conn, table, meta, rowValues);
                    rowValues.clear();
                }

                log.debug("Dumped {} rows from table {}", rowCount, table);
            }
        }

        writer.write("\n");
        writer.flush();
    }

    private static void flushInsertBatch(BufferedWriter writer, Connection conn, String table,
                                         ResultSetMetaData meta, List<String> rowValues) throws IOException, SQLException {
        writer.write("INSERT INTO " + escapeIdentifier(conn, table) + " (");
        for (int i = 1; i <= meta.getColumnCount(); i++) {
            if (i > 1) writer.write(", ");
            writer.write(escapeIdentifier(conn, meta.getColumnName(i)));
        }
        writer.write(") VALUES\n");

        for (int i = 0; i < rowValues.size(); i++) {
            writer.write("  " + rowValues.get(i));
            if (i < rowValues.size() - 1) {
                writer.write(",\n");
            } else {
                writer.write(";\n");
            }
        }
    }

    public static void restoreSqlStream(Connection conn, InputStream in, TableFilter filter) throws IOException, SQLException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            StringBuilder statement = new StringBuilder();
            String line;
            conn.setAutoCommit(false);
            try (Statement stmt = conn.createStatement()) {
                while ((line = reader.readLine()) != null) {
                    String trimmed = line.trim();
                    if (trimmed.startsWith("--") || trimmed.startsWith("/*") || trimmed.isEmpty()) {
                        continue;
                    }
                    statement.append(line).append(" ");
                    if (trimmed.endsWith(";")) {
                        String sql = statement.toString().trim();
                        statement.setLength(0);
                        if (!sql.isEmpty()) {
                            try {
                                stmt.execute(sql);
                            } catch (SQLException e) {
                                log.warn("Warning executing statement during restore (ignoring non-fatal): {}", e.getMessage());
                            }
                        }
                    }
                }
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    private static String extractCreateTable(Connection conn, String table) {
        // Try MySQL SHOW CREATE TABLE
        try (Statement s = conn.createStatement();
             ResultSet rs = s.executeQuery("SHOW CREATE TABLE " + table)) {
            if (rs.next()) {
                return rs.getString(2);
            }
        } catch (Exception ignored) {
        }

        // Try SQLite schema
        try (PreparedStatement ps = conn.prepareStatement("SELECT sql FROM sqlite_master WHERE type='table' AND name=?")) {
            ps.setString(1, table);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("sql");
                }
            }
        } catch (Exception ignored) {
        }

        // Generic JDBC column fallback
        try {
            DatabaseMetaData meta = conn.getMetaData();
            StringBuilder ddl = new StringBuilder("CREATE TABLE " + escapeIdentifier(conn, table) + " (\n");
            try (ResultSet columns = meta.getColumns(null, null, table, "%")) {
                boolean first = true;
                while (columns.next()) {
                    if (!first) ddl.append(",\n");
                    first = false;
                    String colName = columns.getString("COLUMN_NAME");
                    String typeName = columns.getString("TYPE_NAME");
                    int colSize = columns.getInt("COLUMN_SIZE");
                    int nullable = columns.getInt("NULLABLE");

                    ddl.append("  ").append(escapeIdentifier(conn, colName)).append(" ").append(typeName);
                    if (colSize > 0 && (typeName.equalsIgnoreCase("VARCHAR") || typeName.equalsIgnoreCase("CHAR"))) {
                        ddl.append("(").append(colSize).append(")");
                    }
                    if (nullable == DatabaseMetaData.columnNoNulls) {
                        ddl.append(" NOT NULL");
                    }
                }
            }
            ddl.append("\n)");
            return ddl.toString();
        } catch (Exception e) {
            log.warn("Could not generate DDL for table {}: {}", table, e.getMessage());
            return null;
        }
    }

    private static String escapeIdentifier(Connection conn, String name) {
        try {
            String quote = conn.getMetaData().getIdentifierQuoteString();
            if (quote != null && !quote.isBlank()) {
                return quote + name + quote;
            }
        } catch (Exception ignored) {
        }
        return "`" + name + "`";
    }

    private static String escapeSqlString(String s) {
        return s.replace("'", "''").replace("\\", "\\\\");
    }
}

