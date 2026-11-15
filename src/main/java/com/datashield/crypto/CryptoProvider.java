package com.datashield.crypto;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Enterprise cryptographic interface for database archives at rest.
 */
public interface CryptoProvider {
    OutputStream wrapEncryptor(OutputStream target, String password) throws IOException;
    InputStream wrapDecryptor(InputStream source, String password) throws IOException;
    String getAlgorithm();
}

