package com.datashield.util;

import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;

public final class CryptoUtils {
    public static final byte[] MAGIC_HEADER = "DSHIELD1".getBytes();
    public static final int SALT_LENGTH = 16;
    public static final int IV_LENGTH = 12; // Standard 96-bit IV for GCM
    public static final int TAG_LENGTH_BITS = 128;
    public static final int ITERATION_COUNT = 65536;
    public static final int KEY_LENGTH_BITS = 256;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private CryptoUtils() {
    }

    public static byte[] generateRandomBytes(int length) {
        byte[] bytes = new byte[length];
        SECURE_RANDOM.nextBytes(bytes);
        return bytes;
    }

    public static SecretKey deriveKey(char[] password, byte[] salt) {
        try {
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            KeySpec spec = new PBEKeySpec(password, salt, ITERATION_COUNT, KEY_LENGTH_BITS);
            SecretKey secret = factory.generateSecret(spec);
            return new SecretKeySpec(secret.getEncoded(), "AES");
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new IllegalStateException("Failed to derive encryption key", e);
        }
    }

    public static GCMParameterSpec createGcmSpec(byte[] iv) {
        return new GCMParameterSpec(TAG_LENGTH_BITS, iv);
    }
}

