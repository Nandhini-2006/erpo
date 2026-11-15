package com.datashield.core.model;

/**
 * Supported compression algorithms.
 */
public enum CompressionType {
    NONE(".bak"),
    GZIP(".gz"),
    ZIP(".zip");

    private final String extension;

    CompressionType(String extension) {
        this.extension = extension;
    }

    public String getExtension() {
        return extension;
    }

    public static CompressionType fromString(String val) {
        if (val == null || val.isBlank()) {
            return GZIP;
        }
        String clean = val.trim().toUpperCase();
        if (clean.equals("GZ")) return GZIP;
        return CompressionType.valueOf(clean);
    }
}

