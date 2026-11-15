package com.datashield.compression;

import com.datashield.core.model.CompressionType;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Strategy interface for stream-based compression and decompression.
 */
public interface CompressionProvider {
    OutputStream wrapCompressor(OutputStream target) throws IOException;
    InputStream wrapDecompressor(InputStream source) throws IOException;
    CompressionType getType();
}

