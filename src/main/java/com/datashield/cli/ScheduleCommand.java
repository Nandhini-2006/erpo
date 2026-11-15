package com.datashield.cli;

import com.datashield.cli.config.CliConfig;
import com.datashield.cli.config.ConfigLoader;
import com.datashield.core.catalog.BackupCatalog;
import com.datashield.core.catalog.JsonFileBackupCatalog;
import com.datashield.core.model.BackupRequest;
import com.datashield.core.model.ConnectionConfig;
import com.datashield.core.pipeline.BackupPipeline;
import com.datashield.scheduler.BackupSchedulerService;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.File;
import java.util.concurrent.Callable;

@Command(name = "schedule", description = "Automate and schedule database backups via daemon or OS schedulers", mixinStandardHelpOptions = true)
public class ScheduleCommand implements Callable<Integer> {

    @Option(names = {"--config"}, description = "Path to configuration file")
    private String configFile;

    @Option(names = {"--daemon"}, description = "Run as a continuous background daemon process")
    private boolean daemon = false;

    @Option(names = {"--interval-minutes"}, description = "Interval in minutes between backup runs for daemon (default: 1440 = 24h)")
    private long intervalMinutes = 1440;

    @Option(names = {"--export-cron"}, description = "Generate standard Linux crontab configuration (e.g. '0 2 * * *')")
    private String exportCron;

    @Option(names = {"--export-windows"}, description = "Generate Windows Task Scheduler PowerShell automation script")
    private boolean exportWindows = false;

    @Override
    public Integer call() {
        try {
            String jarPath = new File(DataShieldCli.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getAbsolutePath();
            String configPath = configFile != null ? new File(configFile).getAbsolutePath() : "datashield.yaml";

            if (exportCron != null && !exportCron.isBlank()) {
                System.out.println("Generated Linux Crontab Entry:");
                System.out.println("--------------------------------------------------------------------------------");
                System.out.print(BackupSchedulerService.generateLinuxCrontab(exportCron, jarPath, configPath));
                System.out.println("--------------------------------------------------------------------------------");
                System.out.println("To install: run 'crontab -e' and paste the line above.");
                return 0;
            }

            if (exportWindows) {
                System.out.println("Generated Windows PowerShell Task Scheduler Script:");
                System.out.println("--------------------------------------------------------------------------------");
                System.out.print(BackupSchedulerService.generateWindowsScheduledTaskScript("DataShieldBackup", "24", jarPath, configPath));
                System.out.println("--------------------------------------------------------------------------------");
                System.out.println("To install: Run PowerShell as Administrator and execute the script above.");
                return 0;
            }

            if (daemon) {
                System.out.println("=================================================");
                System.out.println("       DataShield Daemon Scheduler Active        ");
                System.out.println("=================================================");
                System.out.printf("Scheduling recurring backup every %d minutes...%n", intervalMinutes);

                CliConfig cfg = ConfigLoader.load(configFile);
                BackupRequest req = new BackupRequest();
                ConnectionConfig conn = req.getConnectionConfig();
                conn.setDatabaseType(cfg.getDatabaseType());
                conn.setHost(cfg.getHost());
                conn.setPort(cfg.getPort());
                conn.setDatabase(cfg.getDatabase());
                conn.setUsername(cfg.getUsername());
                conn.setPassword(cfg.getPassword());
                conn.setConnectionUri(cfg.getUri());

                req.setBackupType(cfg.getBackupType());
                req.setCompression(cfg.getCompression());
                req.setEncryptionPassword(cfg.getEncryptionPassword());
                req.setStorageDestination(cfg.getDestination());
                req.setRetentionDays(cfg.getRetentionDays());
                req.setSlackWebhookUrl(cfg.getSlackWebhook());

                BackupCatalog catalog = new JsonFileBackupCatalog();
                BackupPipeline pipeline = new BackupPipeline(catalog);

                BackupSchedulerService service = new BackupSchedulerService();
                service.schedulePeriodicBackup(pipeline, req, intervalMinutes);

                System.out.println("Daemon started. Press Ctrl+C to terminate.");
                Thread.currentThread().join();
                return 0;
            }

            System.out.println("Please specify an action: --daemon, --export-cron, or --export-windows. Use --help for usage details.");
            return 1;
        } catch (Exception e) {
            System.err.println("Schedule command error: " + e.getMessage());
            e.printStackTrace(System.err);
            return 1;
        }
    }
}

