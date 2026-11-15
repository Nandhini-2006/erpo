package com.datashield.cli;

import com.datashield.cli.config.CliConfig;
import com.datashield.cli.config.ConfigLoader;
import com.datashield.core.catalog.BackupCatalog;
import com.datashield.core.catalog.JsonFileBackupCatalog;
import com.datashield.core.model.*;
import com.datashield.core.pipeline.BackupPipeline;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.util.Arrays;
import java.util.HashSet;
import java.util.concurrent.Callable;

@Command(name = "backup", description = "Execute a full, incremental, or differential database backup", mixinStandardHelpOptions = true)
public class BackupCommand implements Callable<Integer> {

    @Option(names = {"--config"}, description = "Path to configuration file (YAML or JSON)")
    private String configFile;

    @Option(names = {"-t", "--type"}, description = "Backup type: FULL, INCREMENTAL, DIFFERENTIAL (default: FULL)")
    private BackupType backupType;

    @Option(names = {"-d", "--db-type"}, description = "Database type: MYSQL, POSTGRESQL, MONGODB, SQLITE, GENERIC_JDBC")
    private DatabaseType dbType;

    @Option(names = {"-H", "--host"}, description = "Database server host (default: localhost)")
    private String host;

    @Option(names = {"-P", "--port"}, description = "Database server port")
    private Integer port;

    @Option(names = {"-u", "--username"}, description = "Database username")
    private String username;

    @Option(names = {"-p", "--password"}, description = "Database password")
    private String password;

    @Option(names = {"-D", "--database"}, description = "Database name or SQLite file path")
    private String database;

    @Option(names = {"--uri"}, description = "Direct connection URI (overrides host/port)")
    private String uri;

    @Option(names = {"-c", "--compression"}, description = "Compression type: NONE, GZIP, ZIP (default: GZIP)")
    private CompressionType compression;

    @Option(names = {"-e", "--encrypt"}, description = "Encryption password for AES-256-GCM protection")
    private String encryptPassword;

    @Option(names = {"-o", "--dest"}, description = "Destination directory or cloud URI (e.g. ./backups or s3://my-bucket/db-backups)")
    private String destination;

    @Option(names = {"--tables"}, description = "Comma-separated list of specific tables/collections to include")
    private String tables;

    @Option(names = {"--exclude-tables"}, description = "Comma-separated list of tables/collections to exclude")
    private String excludeTables;

    @Option(names = {"--retention-days"}, description = "Retention period in days for local backup pruning")
    private Integer retentionDays;

    @Option(names = {"--slack-webhook"}, description = "Slack incoming webhook URL for notifications")
    private String slackWebhook;

    @Option(names = {"--dry-run"}, description = "Validate connectivity and parameters without writing data")
    private boolean dryRun = false;

    @Override
    public Integer call() {
        try {
            CliConfig cfg = ConfigLoader.load(configFile);

            BackupRequest req = new BackupRequest();
            ConnectionConfig conn = req.getConnectionConfig();

            conn.setDatabaseType(dbType != null ? dbType : cfg.getDatabaseType());
            conn.setHost(host != null ? host : cfg.getHost());
            conn.setPort(port != null ? port : cfg.getPort());
            conn.setDatabase(database != null ? database : cfg.getDatabase());
            conn.setUsername(username != null ? username : cfg.getUsername());
            conn.setPassword(password != null ? password : cfg.getPassword());
            if (uri != null || cfg.getUri() != null) {
                conn.setConnectionUri(uri != null ? uri : cfg.getUri());
            }

            req.setBackupType(backupType != null ? backupType : cfg.getBackupType());
            req.setCompression(compression != null ? compression : cfg.getCompression());
            req.setEncryptionPassword(encryptPassword != null ? encryptPassword : cfg.getEncryptionPassword());
            req.setStorageDestination(destination != null ? destination : cfg.getDestination());
            req.setRetentionDays(retentionDays != null ? retentionDays : cfg.getRetentionDays());
            req.setSlackWebhookUrl(slackWebhook != null ? slackWebhook : cfg.getSlackWebhook());
            req.setDryRun(dryRun);

            if (tables != null && !tables.isBlank()) {
                req.setIncludeTables(new HashSet<>(Arrays.asList(tables.split(","))));
            } else if (cfg.getIncludeTables() != null && !cfg.getIncludeTables().isEmpty()) {
                req.setIncludeTables(cfg.getIncludeTables());
            }

            if (excludeTables != null && !excludeTables.isBlank()) {
                req.setExcludeTables(new HashSet<>(Arrays.asList(excludeTables.split(","))));
            } else if (cfg.getExcludeTables() != null && !cfg.getExcludeTables().isEmpty()) {
                req.setExcludeTables(cfg.getExcludeTables());
            }

            BackupCatalog catalog = new JsonFileBackupCatalog();
            BackupPipeline pipeline = new BackupPipeline(catalog);

            BackupExecutionSummary summary = pipeline.execute(req);
            return summary.isSuccess() ? 0 : 1;
        } catch (Exception e) {
            System.err.println("Backup failed with unhandled exception: " + e.getMessage());
            e.printStackTrace(System.err);
            return 1;
        }
    }
}

