package com.datashield.compression;

import com.datashield.core.model.CompressionType;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class GzipCompressionProvider implements CompressionProvider {
    private static final int BUFFER_SIZE = 64 * 1024; // 64 KB streaming buffer

    @Override
    public OutputStream wrapCompressor(OutputStream target) throws IOException {
        return new GZIPOutputStream(new BufferedOutputStream(target, BUFFER_SIZE), BUFFER_SIZE);
    }

    @Override
    public InputStream wrapDecompressor(InputStream source) throws IOException {
        return new GZIPInputStream(new BufferedInputStream(source, BUFFER_SIZE), BUFFER_SIZE);
    }

    @Override
    public CompressionType getType() {
        return CompressionType.GZIP;
    }
}

