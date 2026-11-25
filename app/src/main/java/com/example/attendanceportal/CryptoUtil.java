package com.example.attendanceportal;

import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/** Password hashing: PBKDF2-HMAC-SHA256, 100,000 iterations, random salt. Passwords are never stored. */
public final class CryptoUtil {
    private static final int ITERATIONS = 100_000;
    private static final int KEY_BITS = 256;

    private CryptoUtil() { }

    public static String newSalt() {
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        return Base64.encodeToString(salt, Base64.NO_WRAP);
    }

    public static String hashPassword(String password, String saltB64) throws Exception {
        byte[] salt = Base64.decode(saltB64, Base64.NO_WRAP);
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_BITS);
        byte[] hash = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        spec.clearPassword();
        return Base64.encodeToString(hash, Base64.NO_WRAP);
    }

    /** Constant-time comparison. */
    public static boolean slowEquals(String a, String b) {
        if (a == null || b == null) return false;
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }
}
