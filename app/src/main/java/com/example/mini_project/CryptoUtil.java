package com.example.mini_project;

import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.SecureRandom;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;

/**
 * One class for everything:
 *  - PIN hashing with PBKDF2-HMAC-SHA256 + random salt
 *  - AES-256-GCM encryption/decryption with a key stored in the Android Keystore
 */
public class CryptoUtil {

    private static final String KEY_ALIAS = "securecampus_aes_key";
    private static final int PBKDF2_ITERATIONS = 100000;
    private static final int PBKDF2_KEY_BITS = 256;

    // ---------- PIN hashing ----------
    public static String newSalt() {
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        return Base64.encodeToString(salt, Base64.NO_WRAP);
    }

    public static String hashPin(String pin, String saltB64) throws Exception {
        byte[] salt = Base64.decode(saltB64, Base64.NO_WRAP);
        PBEKeySpec spec = new PBEKeySpec(pin.toCharArray(), salt,
                PBKDF2_ITERATIONS, PBKDF2_KEY_BITS);
        byte[] hash = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(spec).getEncoded();
        return Base64.encodeToString(hash, Base64.NO_WRAP);
    }

    /** Constant-time comparison (prevents timing attacks). */
    public static boolean slowEquals(String a, String b) {
        if (a == null || b == null) return false;
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8),
                                     b.getBytes(StandardCharsets.UTF_8));
    }

    // ---------- AES-GCM with Keystore ----------
    private static SecretKey getKey() throws Exception {
        KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
        ks.load(null);
        if (!ks.containsAlias(KEY_ALIAS)) {
            KeyGenerator kg = KeyGenerator.getInstance(
                    KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
            kg.init(new KeyGenParameterSpec.Builder(KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build());
            return kg.generateKey();
        }
        return (SecretKey) ks.getKey(KEY_ALIAS, null);
    }

    /** Returns Base64(iv + ciphertext). */
    public static String encrypt(String plain) throws Exception {
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.ENCRYPT_MODE, getKey());
        byte[] iv = c.getIV();
        byte[] ct = c.doFinal(plain.getBytes(StandardCharsets.UTF_8));
        byte[] out = new byte[iv.length + ct.length];
        System.arraycopy(iv, 0, out, 0, iv.length);
        System.arraycopy(ct, 0, out, iv.length, ct.length);
        return Base64.encodeToString(out, Base64.NO_WRAP);
    }

    public static String decrypt(String encoded) throws Exception {
        byte[] all = Base64.decode(encoded, Base64.NO_WRAP);
        byte[] iv = new byte[12];
        System.arraycopy(all, 0, iv, 0, 12);
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.DECRYPT_MODE, getKey(), new GCMParameterSpec(128, iv));
        byte[] pt = c.doFinal(all, 12, all.length - 12);
        return new String(pt, StandardCharsets.UTF_8);
    }

    /** Deletes the AES key from the Keystore. Used by "Erase all data". */
    public static void deleteKey() throws Exception {
        KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
        ks.load(null);
        if (ks.containsAlias(KEY_ALIAS)) ks.deleteEntry(KEY_ALIAS);
    }

    // ---------- File encryption (document vault) ----------
    /** Encrypts a stream into outFile as [12-byte IV][AES-GCM ciphertext + tag]. */
    public static void encryptStream(InputStream in, File outFile, long maxBytes) throws Exception {
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.ENCRYPT_MODE, getKey());
        try (FileOutputStream fos = new FileOutputStream(outFile)) {
            fos.write(c.getIV());
            try (CipherOutputStream cos = new CipherOutputStream(fos, c)) {
                byte[] buf = new byte[8192];
                long total = 0;
                int n;
                while ((n = in.read(buf)) != -1) {
                    total += n;
                    if (total > maxBytes) {
                        throw new IOException("File is larger than " + (maxBytes / 1024 / 1024) + " MB");
                    }
                    cos.write(buf, 0, n);
                }
            }
        }
    }

    /** Decrypts a vault file into memory. Fails if the file was tampered with (GCM tag check). */
    public static byte[] decryptFile(File f) throws Exception {
        try (FileInputStream fis = new FileInputStream(f)) {
            byte[] iv = new byte[12];
            new DataInputStream(fis).readFully(iv);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, getKey(), new GCMParameterSpec(128, iv));
            try (CipherInputStream cis = new CipherInputStream(fis, c)) {
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n;
                while ((n = cis.read(buf)) != -1) bos.write(buf, 0, n);
                return bos.toByteArray();
            }
        }
    }
}
