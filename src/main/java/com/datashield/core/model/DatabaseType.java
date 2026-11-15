package com.datashield.core.model;

/**
 * Supported Database Management Systems.
 */
public enum DatabaseType {
    MYSQL("MySQL", 3306, "jdbc:mysql://%s:%d/%s"),
    POSTGRESQL("PostgreSQL", 5432, "jdbc:postgresql://%s:%d/%s"),
    MONGODB("MongoDB", 27017, "mongodb://%s:%d/%s"),
    SQLITE("SQLite", 0, "jdbc:sqlite:%s"),
    GENERIC_JDBC("Generic JDBC", 0, "%s");

    private final String displayName;
    private final int defaultPort;
    private final String urlFormat;

    DatabaseType(String displayName, int defaultPort, String urlFormat) {
        this.displayName = displayName;
        this.defaultPort = defaultPort;
        this.urlFormat = urlFormat;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getDefaultPort() {
        return defaultPort;
    }

    public String getUrlFormat() {
        return urlFormat;
    }

    public static DatabaseType fromString(String val) {
        if (val == null || val.isBlank()) {
            throw new IllegalArgumentException("Database type cannot be null or empty");
        }
        String clean = val.trim().toUpperCase().replace("-", "_");
        if (clean.equals("POSTGRES") || clean.equals("PG")) {
            return POSTGRESQL;
        }
        if (clean.equals("MONGO")) {
            return MONGODB;
        }
        return DatabaseType.valueOf(clean);
    }
}

