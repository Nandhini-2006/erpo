package com.datashield.core.pipeline;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;

public class ProgressOutputStream extends FilterOutputStream {
    private static final Logger log = LoggerFactory.getLogger(ProgressOutputStream.class);
    private long bytesWritten = 0;
    private long lastReportBytes = 0;
    private long lastReportTime = System.currentTimeMillis();

    public ProgressOutputStream(OutputStream out) {
        super(out);
    }

    @Override
    public void write(int b) throws IOException {
        out.write(b);
        bytesWritten++;
        checkProgress();
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        out.write(b, off, len);
        bytesWritten += len;
        checkProgress();
    }

    private void checkProgress() {
        // Report progress every 10 MB or every 5 seconds
        long now = System.currentTimeMillis();
        if (bytesWritten - lastReportBytes >= 10 * 1024 * 1024 || now - lastReportTime >= 5000) {
            double mb = bytesWritten / (1024.0 * 1024.0);
            double deltaSec = (now - lastReportTime) / 1000.0;
            double speed = deltaSec > 0 ? ((bytesWritten - lastReportBytes) / (1024.0 * 1024.0)) / deltaSec : 0;
            log.info("Processed: {:.2f} MB ({:.2f} MB/s)", mb, speed);
            lastReportBytes = bytesWritten;
            lastReportTime = now;
        }
    }

    public long getBytesWritten() {
        return bytesWritten;
    }
}

