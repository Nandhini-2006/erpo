package com.datashield.cli;

import com.datashield.cli.config.CliConfig;
import com.datashield.cli.config.ConfigLoader;
import com.datashield.core.catalog.BackupCatalog;
import com.datashield.core.catalog.JsonFileBackupCatalog;
import com.datashield.core.model.BackupExecutionSummary;
import com.datashield.core.model.ConnectionConfig;
import com.datashield.core.model.DatabaseType;
import com.datashield.core.model.RestoreRequest;
import com.datashield.core.pipeline.RestorePipeline;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.util.Arrays;
import java.util.HashSet;
import java.util.concurrent.Callable;

@Command(name = "restore", description = "Restore a database from a local or cloud backup archive", mixinStandardHelpOptions = true)
public class RestoreCommand implements Callable<Integer> {

    @Option(names = {"--config"}, description = "Path to configuration file")
    private String configFile;

    @Option(names = {"-s", "--source"}, required = true, description = "Backup archive path, s3:// URI, or backup ID from catalog")
    private String source;

    @Option(names = {"-d", "--db-type"}, description = "Target database type: MYSQL, POSTGRESQL, MONGODB, SQLITE, GENERIC_JDBC")
    private DatabaseType dbType;

    @Option(names = {"-H", "--host"}, description = "Target database server host")
    private String host;

    @Option(names = {"-P", "--port"}, description = "Target database server port")
    private Integer port;

    @Option(names = {"-u", "--username"}, description = "Target database username")
    private String username;

    @Option(names = {"-p", "--password"}, description = "Target database password")
    private String password;

    @Option(names = {"-D", "--database"}, description = "Target database name or SQLite file path")
    private String database;

    @Option(names = {"--uri"}, description = "Target direct connection URI")
    private String uri;

    @Option(names = {"-e", "--decrypt"}, description = "Decryption password if archive is encrypted")
    private String decryptPassword;

    @Option(names = {"--tables"}, description = "Selective tables/collections to restore (comma-separated)")
    private String tables;

    @Option(names = {"--drop"}, description = "Drop existing tables/collections prior to restore")
    private boolean drop = false;

    @Option(names = {"--slack-webhook"}, description = "Slack incoming webhook URL for notification")
    private String slackWebhook;

    @Option(names = {"--dry-run"}, description = "Verify archive integrity and database credentials without executing restore")
    private boolean dryRun = false;

    @Override
    public Integer call() {
        try {
            CliConfig cfg = ConfigLoader.load(configFile);

            RestoreRequest req = new RestoreRequest();
            ConnectionConfig conn = req.getTargetConnectionConfig();

            conn.setDatabaseType(dbType != null ? dbType : cfg.getDatabaseType());
            conn.setHost(host != null ? host : cfg.getHost());
            conn.setPort(port != null ? port : cfg.getPort());
            conn.setDatabase(database != null ? database : cfg.getDatabase());
            conn.setUsername(username != null ? username : cfg.getUsername());
            conn.setPassword(password != null ? password : cfg.getPassword());
            if (uri != null || cfg.getUri() != null) {
                conn.setConnectionUri(uri != null ? uri : cfg.getUri());
            }

            req.setBackupSource(source);
            req.setEncryptionPassword(decryptPassword != null ? decryptPassword : cfg.getEncryptionPassword());
            req.setDropExistingTables(drop);
            req.setSlackWebhookUrl(slackWebhook != null ? slackWebhook : cfg.getSlackWebhook());
            req.setDryRun(dryRun);

            if (tables != null && !tables.isBlank()) {
                req.setSelectiveTables(new HashSet<>(Arrays.asList(tables.split(","))));
            }

            BackupCatalog catalog = new JsonFileBackupCatalog();
            RestorePipeline pipeline = new RestorePipeline(catalog);

            BackupExecutionSummary summary = pipeline.execute(req);
            return summary.isSuccess() ? 0 : 1;
        } catch (Exception e) {
            System.err.println("Restore failed with unhandled exception: " + e.getMessage());
            e.printStackTrace(System.err);
            return 1;
        }
    }
}

