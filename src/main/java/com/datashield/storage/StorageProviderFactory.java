package com.datashield.storage;

public final class StorageProviderFactory {
    private StorageProviderFactory() {
    }

    public static StorageProvider create(String destination) {
        if (destination == null || destination.isBlank()) {
            return new LocalStorageProvider("./backups");
        }

        String trimmed = destination.trim();
        if (trimmed.startsWith("s3://")) {
            return new S3StorageProvider(trimmed);
        } else if (trimmed.startsWith("file://")) {
            return new LocalStorageProvider(trimmed.substring(7));
        } else {
            return new LocalStorageProvider(trimmed);
        }
    }
}

