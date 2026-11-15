package com.datashield.util;

import java.text.DecimalFormat;

public final class ByteUtils {
    private static final String[] UNITS = {"B", "KB", "MB", "GB", "TB", "PB"};
    private static final DecimalFormat FORMAT = new DecimalFormat("#,##0.00");

    private ByteUtils() {
    }

    public static String humanReadableByteCount(long bytes) {
        if (bytes <= 0) return "0 B";
        int digitGroups = (int) (Math.log10(bytes) / Math.log10(1024));
        if (digitGroups >= UNITS.length) {
            digitGroups = UNITS.length - 1;
        }
        return FORMAT.format(bytes / Math.pow(1024, digitGroups)) + " " + UNITS[digitGroups];
    }

    public static String formatDuration(long millis) {
        if (millis < 1000) {
            return millis + " ms";
        }
        long seconds = millis / 1000;
        long s = seconds % 60;
        long m = (seconds / 60) % 60;
        long h = (seconds / (60 * 60));
        if (h > 0) {
            return String.format("%d h %d m %d s", h, m, s);
        } else if (m > 0) {
            return String.format("%d m %d s", m, s);
        } else {
            return String.format("%.2f s", millis / 1000.0);
        }
    }
}

