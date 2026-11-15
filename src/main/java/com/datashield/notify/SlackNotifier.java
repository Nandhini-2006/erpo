package com.datashield.notify;

import com.datashield.core.model.BackupExecutionSummary;
import com.datashield.core.model.BackupManifest;
import com.datashield.util.ByteUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;

/**
 * Enterprise Slack incoming webhook notifier with formatted payload blocks.
 */
public class SlackNotifier implements NotificationService {
    private static final Logger log = LoggerFactory.getLogger(SlackNotifier.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String webhookUrl;
    private final HttpClient httpClient;

    public SlackNotifier(String webhookUrl) {
        this.webhookUrl = webhookUrl;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    @Override
    public void notifyBackupCompleted(BackupExecutionSummary summary, BackupManifest manifest) {
        if (webhookUrl == null || webhookUrl.isBlank()) {
            return;
        }

        try {
            boolean success = summary.isSuccess();
            String title = (success ? "✅ Backup Succeeded: " : "❌ Backup Failed: ")
                    + (manifest != null ? manifest.getDatabaseName() : "Database");
            String color = success ? "#36a64f" : "#dc3545";

            Map<String, Object> payload = new LinkedHashMap<>();
            List<Map<String, Object>> attachments = new ArrayList<>();
            Map<String, Object> attachment = new LinkedHashMap<>();
            attachment.put("color", color);
            attachment.put("title", title);

            StringBuilder text = new StringBuilder();
            text.append("*Operation:* ").append(summary.getOperation()).append("\n");
            text.append("*Status:* ").append(success ? "SUCCESS" : "FAILED").append("\n");
            text.append("*Backup ID:* `").append(summary.getBackupId()).append("`\n");
            text.append("*Duration:* ").append(ByteUtils.formatDuration(summary.getDurationMillis())).append("\n");
            text.append("*Archive Size:* ").append(ByteUtils.humanReadableByteCount(summary.getArchiveBytes())).append("\n");
            if (manifest != null) {
                text.append("*DBMS:* ").append(manifest.getDatabaseType()).append("\n");
                text.append("*Type:* ").append(manifest.getBackupType()).append("\n");
                text.append("*Storage:* ").append(manifest.getStorageLocation()).append("\n");
                text.append("*SHA-256:* `").append(manifest.getSha256Checksum() != null ? manifest.getSha256Checksum().substring(0, Math.min(16, manifest.getSha256Checksum().length())) + "..." : "N/A").append("`\n");
            }
            if (!success && summary.getErrorMessage() != null) {
                text.append("*Error:* ```").append(summary.getErrorMessage()).append("```\n");
            }

            attachment.put("text", text.toString());
            attachment.put("footer", "DataShield Enterprise Backup Utility");
            attachment.put("ts", System.currentTimeMillis() / 1000);

            attachments.add(attachment);
            payload.put("attachments", attachments);

            sendWebhook(payload);
        } catch (Exception e) {
            log.error("Failed to deliver Slack notification", e);
        }
    }

    @Override
    public void notifyRestoreCompleted(BackupExecutionSummary summary) {
        if (webhookUrl == null || webhookUrl.isBlank()) {
            return;
        }

        try {
            boolean success = summary.isSuccess();
            String title = (success ? "✅ Restore Succeeded" : "❌ Restore Failed");
            String color = success ? "#36a64f" : "#dc3545";

            Map<String, Object> payload = new LinkedHashMap<>();
            List<Map<String, Object>> attachments = new ArrayList<>();
            Map<String, Object> attachment = new LinkedHashMap<>();
            attachment.put("color", color);
            attachment.put("title", title);

            StringBuilder text = new StringBuilder();
            text.append("*Operation:* ").append(summary.getOperation()).append("\n");
            text.append("*Status:* ").append(success ? "SUCCESS" : "FAILED").append("\n");
            text.append("*Duration:* ").append(ByteUtils.formatDuration(summary.getDurationMillis())).append("\n");
            text.append("*Destination DB:* ").append(summary.getDestination()).append("\n");
            if (!success && summary.getErrorMessage() != null) {
                text.append("*Error:* ```").append(summary.getErrorMessage()).append("```\n");
            }

            attachment.put("text", text.toString());
            attachment.put("footer", "DataShield Enterprise Backup Utility");
            attachment.put("ts", System.currentTimeMillis() / 1000);

            attachments.add(attachment);
            payload.put("attachments", attachments);

            sendWebhook(payload);
        } catch (Exception e) {
            log.error("Failed to deliver Slack notification for restore", e);
        }
    }

    private void sendWebhook(Map<String, Object> payload) throws Exception {
        String json = MAPPER.writeValueAsString(payload);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(webhookUrl))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(10))
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            log.info("Slack notification dispatched successfully");
        } else {
            log.warn("Slack webhook returned HTTP status: {} response: {}", response.statusCode(), response.body());
        }
    }
}

