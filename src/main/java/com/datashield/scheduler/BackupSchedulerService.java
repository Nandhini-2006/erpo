package com.datashield.scheduler;

import com.datashield.core.model.BackupRequest;
import com.datashield.core.pipeline.BackupPipeline;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Enterprise in-process scheduling service and OS automation helper.
 */
public class BackupSchedulerService {
    private static final Logger log = LoggerFactory.getLogger(BackupSchedulerService.class);

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "datashield-scheduler");
        t.setDaemon(false);
        return t;
    });

    public void schedulePeriodicBackup(BackupPipeline pipeline, BackupRequest request, long intervalMinutes) {
        log.info("Scheduling automated backup every {} minutes (initial delay: 10 seconds)...", intervalMinutes);
        scheduler.scheduleAtFixedRate(() -> {
            try {
                log.info("Triggering scheduled automated backup run...");
                pipeline.execute(request);
            } catch (Exception e) {
                log.error("Scheduled backup run failed", e);
            }
        }, 10, intervalMinutes * 60, TimeUnit.SECONDS);
    }

    public static String generateLinuxCrontab(String cronExpression, String jarPath, String configPath) {
        return String.format("# DataShield Automated Database Backup\n%s /usr/bin/java -jar %s backup --config %s >> /var/log/datashield-cron.log 2>&1\n",
                cronExpression, jarPath, configPath);
    }

    public static String generateWindowsScheduledTaskScript(String taskName, String intervalHours, String jarPath, String configPath) {
        return String.format(
                """
                # PowerShell command to register Windows Scheduled Task for DataShield
                $action = New-ScheduledTaskAction -Execute "java" -Argument "-jar `"%s`" backup --config `"%s`""
                $trigger = New-ScheduledTaskTrigger -Daily -At 2:00AM
                Register-ScheduledTask -TaskName "%s" -Action $action -Trigger $trigger -Description "Enterprise automated database backup by DataShield"
                """, jarPath, configPath, taskName);
    }

    public void stop() {
        scheduler.shutdown();
    }
}

