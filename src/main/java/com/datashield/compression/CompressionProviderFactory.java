package com.datashield.compression;

import com.datashield.core.model.CompressionType;

public final class CompressionProviderFactory {
    private CompressionProviderFactory() {
    }

    public static CompressionProvider getProvider(CompressionType type) {
        if (type == null) {
            return new GzipCompressionProvider();
        }
        return switch (type) {
            case NONE -> new NoneCompressionProvider();
            case GZIP, ZIP -> new GzipCompressionProvider();
        };
    }
}

