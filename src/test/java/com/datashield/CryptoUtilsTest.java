package com.datashield;

import com.datashield.crypto.AesGcmCryptoProvider;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CryptoUtilsTest {

    @Test
    void shouldEncryptAndDecryptStreamWithCorrectPassword() throws Exception {
        AesGcmCryptoProvider crypto = new AesGcmCryptoProvider();
        String originalText = "CRITICAL_DATABASE_PAYLOAD_WITH_ENTERPRISE_TRANSACTIONS_123456789";
        String password = "SecureMasterPassword#2026";

        ByteArrayOutputStream cipherOut = new ByteArrayOutputStream();
        try (OutputStream encryptStream = crypto.wrapEncryptor(cipherOut, password)) {
            encryptStream.write(originalText.getBytes(StandardCharsets.UTF_8));
            encryptStream.flush();
        }

        byte[] cipherBytes = cipherOut.toByteArray();
        assertThat(cipherBytes).isNotEqualTo(originalText.getBytes(StandardCharsets.UTF_8));
        assertThat(cipherBytes.length).isGreaterThan(originalText.length());

        // Decrypt
        ByteArrayInputStream cipherIn = new ByteArrayInputStream(cipherBytes);
        ByteArrayOutputStream decryptedOut = new ByteArrayOutputStream();
        try (InputStream decryptStream = crypto.wrapDecryptor(cipherIn, password)) {
            decryptStream.transferTo(decryptedOut);
        }

        String decryptedText = decryptedOut.toString(StandardCharsets.UTF_8);
        assertThat(decryptedText).isEqualTo(originalText);
    }

    @Test
    void shouldFailDecryptionWhenPasswordIsWrong() throws Exception {
        AesGcmCryptoProvider crypto = new AesGcmCryptoProvider();
        String originalText = "SuperSecretDbPassword";
        String correctPassword = "CorrectPassword123";
        String wrongPassword = "WrongPassword999";

        ByteArrayOutputStream cipherOut = new ByteArrayOutputStream();
        try (OutputStream encryptStream = crypto.wrapEncryptor(cipherOut, correctPassword)) {
            encryptStream.write(originalText.getBytes(StandardCharsets.UTF_8));
            encryptStream.flush();
        }

        ByteArrayInputStream cipherIn = new ByteArrayInputStream(cipherOut.toByteArray());
        ByteArrayOutputStream decryptedOut = new ByteArrayOutputStream();

        assertThatThrownBy(() -> {
            try (InputStream decryptStream = crypto.wrapDecryptor(cipherIn, wrongPassword)) {
                decryptStream.transferTo(decryptedOut);
            }
        }).hasMessageContaining("AEADBadTagException").isInstanceOf(IOException.class);
    }
}

