package com.datashield.core.model;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * Enterprise database connection configuration model.
 */
public class ConnectionConfig implements Serializable {
    private DatabaseType databaseType = DatabaseType.MYSQL;
    private String host = "localhost";
    private int port = 3306;
    private String database;
    private String username;
    private String password;
    private String connectionUri;
    private boolean ssl = false;
    private int connectionTimeoutSeconds = 15;
    private Map<String, String> additionalProperties = new HashMap<>();

    public ConnectionConfig() {
    }

    public ConnectionConfig(DatabaseType databaseType, String host, int port, String database, String username, String password) {
        this.databaseType = databaseType;
        this.host = host;
        this.port = port > 0 ? port : databaseType.getDefaultPort();
        this.database = database;
        this.username = username;
        this.password = password;
    }

    public DatabaseType getDatabaseType() {
        return databaseType;
    }

    public void setDatabaseType(DatabaseType databaseType) {
        this.databaseType = databaseType;
        if (this.port == 0 && databaseType != null) {
            this.port = databaseType.getDefaultPort();
        }
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port > 0 ? port : (databaseType != null ? databaseType.getDefaultPort() : 0);
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getDatabase() {
        return database;
    }

    public void setDatabase(String database) {
        this.database = database;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getConnectionUri() {
        return connectionUri;
    }

    public void setConnectionUri(String connectionUri) {
        this.connectionUri = connectionUri;
    }

    public boolean isSsl() {
        return ssl;
    }

    public void setSsl(boolean ssl) {
        this.ssl = ssl;
    }

    public int getConnectionTimeoutSeconds() {
        return connectionTimeoutSeconds;
    }

    public void setConnectionTimeoutSeconds(int connectionTimeoutSeconds) {
        this.connectionTimeoutSeconds = connectionTimeoutSeconds;
    }

    public Map<String, String> getAdditionalProperties() {
        return additionalProperties;
    }

    public void setAdditionalProperties(Map<String, String> additionalProperties) {
        this.additionalProperties = additionalProperties;
    }

    /**
     * Constructs standard JDBC URL based on DBMS type and settings.
     */
    public String getJdbcUrl() {
        if (connectionUri != null && !connectionUri.isBlank()) {
            return connectionUri;
        }

        return switch (databaseType) {
            case MYSQL -> String.format("jdbc:mysql://%s:%d/%s?useSSL=%s&allowPublicKeyRetrieval=true&serverTimezone=UTC&connectTimeout=%d",
                    host, getPort(), database != null ? database : "", ssl, connectionTimeoutSeconds * 1000);
            case POSTGRESQL -> String.format("jdbc:postgresql://%s:%d/%s?ssl=%s&connectTimeout=%d",
                    host, getPort(), database != null ? database : "", ssl, connectionTimeoutSeconds);
            case SQLITE -> String.format("jdbc:sqlite:%s", host != null && !host.isBlank() ? host : database);
            case MONGODB -> String.format("mongodb://%s%s%s:%d/%s",
                    username != null ? username + (password != null ? ":" + password : "") + "@" : "",
                    host, getPort() > 0 ? ":" + getPort() : "",
                    database != null ? database : "");
            case GENERIC_JDBC -> host;
        };
    }

    @Override
    public String toString() {
        return "ConnectionConfig{" +
                "type=" + databaseType +
                ", host='" + host + '\'' +
                ", port=" + getPort() +
                ", database='" + database + '\'' +
                ", username='" + username + '\'' +
                ", ssl=" + ssl +
                '}';
    }
}

