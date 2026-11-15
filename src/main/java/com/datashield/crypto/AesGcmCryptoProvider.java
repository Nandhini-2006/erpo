package com.datashield.crypto;

import com.datashield.util.CryptoUtils;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.GeneralSecurityException;
import java.util.Arrays;

/**
 * Enterprise AES-256-GCM authenticated streaming encryption provider.
 * Protects against tampering and unauthorized data recovery.
 */
public class AesGcmCryptoProvider implements CryptoProvider {
    private static final String ALGORITHM = "AES_256_GCM";

    @Override
    public OutputStream wrapEncryptor(OutputStream target, String password) throws IOException {
        if (password == null || password.isBlank()) {
            throw new IllegalArgumentException("Encryption password cannot be blank");
        }

        try {
            byte[] salt = CryptoUtils.generateRandomBytes(CryptoUtils.SALT_LENGTH);
            byte[] iv = CryptoUtils.generateRandomBytes(CryptoUtils.IV_LENGTH);

            SecretKey secretKey = CryptoUtils.deriveKey(password.toCharArray(), salt);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, CryptoUtils.createGcmSpec(iv));

            // Write archive header: MAGIC (8B) + SALT (16B) + IV (12B)
            target.write(CryptoUtils.MAGIC_HEADER);
            target.write(salt);
            target.write(iv);
            target.flush();

            return new CipherOutputStream(target, cipher);
        } catch (GeneralSecurityException e) {
            throw new IOException("Failed to initialize AES-GCM cipher for encryption", e);
        }
    }

    @Override
    public InputStream wrapDecryptor(InputStream source, String password) throws IOException {
        if (password == null || password.isBlank()) {
            throw new IllegalArgumentException("Decryption password cannot be blank");
        }

        try {
            // Read and verify magic header
            byte[] magic = source.readNBytes(CryptoUtils.MAGIC_HEADER.length);
            if (!Arrays.equals(magic, CryptoUtils.MAGIC_HEADER)) {
                throw new IOException("Invalid encrypted archive header. Archive is not encrypted with DataShield AES-GCM or is corrupted.");
            }

            // Read salt and IV
            byte[] salt = source.readNBytes(CryptoUtils.SALT_LENGTH);
            if (salt.length != CryptoUtils.SALT_LENGTH) {
                throw new IOException("Truncated archive header: missing salt bytes");
            }

            byte[] iv = source.readNBytes(CryptoUtils.IV_LENGTH);
            if (iv.length != CryptoUtils.IV_LENGTH) {
                throw new IOException("Truncated archive header: missing IV bytes");
            }

            SecretKey secretKey = CryptoUtils.deriveKey(password.toCharArray(), salt);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(CryptoUtils.TAG_LENGTH_BITS, iv));

            return new CipherInputStream(source, cipher);
        } catch (GeneralSecurityException e) {
            throw new IOException("Failed to initialize AES-GCM cipher for decryption. Check password.", e);
        }
    }

    @Override
    public String getAlgorithm() {
        return ALGORITHM;
    }
}

