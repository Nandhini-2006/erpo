package com.datashield.cli;

import com.datashield.adapter.DatabaseAdapter;
import com.datashield.adapter.DatabaseAdapterFactory;
import com.datashield.cli.config.CliConfig;
import com.datashield.cli.config.ConfigLoader;
import com.datashield.core.model.ConnectionConfig;
import com.datashield.core.model.DatabaseType;
import com.datashield.storage.StorageProvider;
import com.datashield.storage.StorageProviderFactory;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.util.concurrent.Callable;

@Command(name = "test-connection", description = "Test connectivity and credentials for a database or storage target", mixinStandardHelpOptions = true)
public class TestConnectionCommand implements Callable<Integer> {

    @Option(names = {"--config"}, description = "Path to configuration file")
    private String configFile;

    @Option(names = {"-d", "--db-type"}, description = "Database type: MYSQL, POSTGRESQL, MONGODB, SQLITE, GENERIC_JDBC")
    private DatabaseType dbType;

    @Option(names = {"-H", "--host"}, description = "Database server host")
    private String host;

    @Option(names = {"-P", "--port"}, description = "Database server port")
    private Integer port;

    @Option(names = {"-u", "--username"}, description = "Database user")
    private String username;

    @Option(names = {"-p", "--password"}, description = "Database password", interactive = false)
    private String password;

    @Option(names = {"-D", "--database"}, description = "Database name or SQLite file path")
    private String database;

    @Option(names = {"--uri"}, description = "Direct connection URI")
    private String uri;

    @Option(names = {"--storage"}, description = "Test cloud/local storage target connectivity (e.g. s3://bucket or ./backups)")
    private String storageTarget;

    @Override
    public Integer call() {
        System.out.println("=================================================");
        System.out.println("      DataShield Pre-flight Connectivity Test    ");
        System.out.println("=================================================");

        try {
            CliConfig cfg = ConfigLoader.load(configFile);
            DatabaseType type = dbType != null ? dbType : cfg.getDatabaseType();
            String resolvedHost = host != null ? host : cfg.getHost();
            int resolvedPort = port != null ? port : cfg.getPort();
            String resolvedDb = database != null ? database : cfg.getDatabase();
            String resolvedUser = username != null ? username : cfg.getUsername();
            String resolvedPass = password != null ? password : cfg.getPassword();
            String resolvedUri = uri != null ? uri : cfg.getUri();

            ConnectionConfig conn = new ConnectionConfig(type, resolvedHost, resolvedPort, resolvedDb, resolvedUser, resolvedPass);
            if (resolvedUri != null) conn.setConnectionUri(resolvedUri);

            System.out.printf("[1/2] Testing Database Connectivity (%s)...%n", type);
            DatabaseAdapter adapter = DatabaseAdapterFactory.getAdapter(type);
            adapter.testConnection(conn);
            System.out.println(">>> Database Connection: SUCCESS! Credentials and access validated.\n");

            if (storageTarget != null || cfg.getDestination() != null) {
                String st = storageTarget != null ? storageTarget : cfg.getDestination();
                System.out.printf("[2/2] Testing Storage Target Connectivity (%s)...%n", st);
                StorageProvider storage = StorageProviderFactory.create(st);
                storage.testConnectivity();
                System.out.println(">>> Storage Target: SUCCESS! Storage is reachable and writable.\n");
            }

            System.out.println("ALL CHECKS PASSED. Ready for backup/restore operations.");
            return 0;
        } catch (Exception e) {
            System.err.println("\nCONNECTION TEST FAILED: " + e.getMessage());
            e.printStackTrace(System.err);
            return 1;
        }
    }
}

