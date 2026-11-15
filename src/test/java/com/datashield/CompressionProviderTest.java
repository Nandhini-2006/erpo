package com.datashield;

import com.datashield.compression.CompressionProvider;
import com.datashield.compression.GzipCompressionProvider;
import com.datashield.compression.NoneCompressionProvider;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class CompressionProviderTest {

    @Test
    void shouldCompressAndDecompressWithGzip() throws Exception {
        CompressionProvider gzip = new GzipCompressionProvider();
        String payload = "Lorem ipsum dolor sit amet, consectetur adipiscing elit. ".repeat(100);

        ByteArrayOutputStream compressedOut = new ByteArrayOutputStream();
        try (OutputStream out = gzip.wrapCompressor(compressedOut)) {
            out.write(payload.getBytes(StandardCharsets.UTF_8));
        }

        byte[] compressedBytes = compressedOut.toByteArray();
        assertThat(compressedBytes.length).isLessThan(payload.length());

        ByteArrayInputStream in = new ByteArrayInputStream(compressedBytes);
        ByteArrayOutputStream decompressedOut = new ByteArrayOutputStream();
        try (InputStream decompressStream = gzip.wrapDecompressor(in)) {
            decompressStream.transferTo(decompressedOut);
        }

        assertThat(decompressedOut.toString(StandardCharsets.UTF_8)).isEqualTo(payload);
    }

    @Test
    void shouldPassThroughWithNoneCompression() throws Exception {
        CompressionProvider none = new NoneCompressionProvider();
        String payload = "Uncompressed raw data stream";

        ByteArrayOutputStream outStream = new ByteArrayOutputStream();
        try (OutputStream out = none.wrapCompressor(outStream)) {
            out.write(payload.getBytes(StandardCharsets.UTF_8));
        }

        assertThat(outStream.toString(StandardCharsets.UTF_8)).isEqualTo(payload);
    }
}

