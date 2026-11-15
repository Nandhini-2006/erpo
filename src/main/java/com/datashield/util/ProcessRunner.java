package com.datashield.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Enterprise process runner handling process piping, stderr streaming,
 * credential masking, and timeout controls.
 */
public class ProcessRunner {
    private static final Logger log = LoggerFactory.getLogger(ProcessRunner.class);

    public static boolean isCommandAvailable(String command) {
        String testCmd = System.getProperty("os.name").toLowerCase().contains("win") ? "where" : "which";
        try {
            ProcessBuilder pb = new ProcessBuilder(testCmd, command);
            pb.redirectErrorStream(true);
            Process p = pb.start();
            boolean finished = p.waitFor(3, TimeUnit.SECONDS);
            return finished && p.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    public static Process startProcess(List<String> command, Map<String, String> environment) throws IOException {
        log.debug("Spawning process: {}", sanitizeCommand(command));
        ProcessBuilder pb = new ProcessBuilder(command);
        if (environment != null && !environment.isEmpty()) {
            pb.environment().putAll(environment);
        }
        return pb.start();
    }

    public static String captureStderr(Process process) {
        StringBuilder sb = new StringBuilder();
        Thread thread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append(System.lineSeparator());
                }
            } catch (IOException ignored) {
            }
        });
        thread.setDaemon(true);
        thread.start();
        return sb.toString();
    }

    private static String sanitizeCommand(List<String> command) {
        StringBuilder sb = new StringBuilder();
        for (String arg : command) {
            if (arg.toLowerCase().contains("password") || arg.startsWith("-p") && arg.length() > 2) {
                sb.append("****** ");
            } else {
                sb.append(arg).append(" ");
            }
        }
        return sb.toString().trim();
    }
}

