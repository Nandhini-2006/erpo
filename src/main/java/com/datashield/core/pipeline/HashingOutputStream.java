package com.datashield.core.pipeline;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * FilterOutputStream that calculates SHA-256 cryptographic digest and total bytes written
 * in real-time as data streams through without buffering in memory.
 */
public class HashingOutputStream extends FilterOutputStream {
    private final MessageDigest digest;
    private long totalBytes = 0;

    public HashingOutputStream(OutputStream out) {
        super(out);
        try {
            this.digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 digest algorithm unavailable", e);
        }
    }

    @Override
    public void write(int b) throws IOException {
        out.write(b);
        digest.update((byte) b);
        totalBytes++;
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        out.write(b, off, len);
        digest.update(b, off, len);
        totalBytes += len;
    }

    public long getTotalBytes() {
        return totalBytes;
    }

    public String getSha256Checksum() {
        return HexFormat.of().formatHex(digest.digest());
    }
}

