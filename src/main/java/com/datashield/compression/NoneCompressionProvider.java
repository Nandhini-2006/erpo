package com.datashield.compression;

import com.datashield.core.model.CompressionType;

import java.io.InputStream;
import java.io.OutputStream;

public class NoneCompressionProvider implements CompressionProvider {
    @Override
    public OutputStream wrapCompressor(OutputStream target) {
        return target;
    }

    @Override
    public InputStream wrapDecompressor(InputStream source) {
        return source;
    }

    @Override
    public CompressionType getType() {
        return CompressionType.NONE;
    }
}

