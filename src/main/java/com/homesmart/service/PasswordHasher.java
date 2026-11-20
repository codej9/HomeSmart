package com.homesmart.service;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Password hashing with PBKDF2-HMAC-SHA256.
 * A stored hash looks like {@code pbkdf2-sha256$600000$<salt>$<hash>}, with Base64 salt and hash.
 */
public final class PasswordHasher {

    private static final String SCHEME = "pbkdf2-sha256";
    private static final int ITERATIONS = 600_000;
    private static final int SALT_BYTES = 16;
    private static final int KEY_BITS = 256;
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final byte[] DUMMY_SALT = new byte[SALT_BYTES];

    private PasswordHasher() {
    }

    public static String hash(String password) {
        byte[] salt = new byte[SALT_BYTES];
        RANDOM.nextBytes(salt);
        Base64.Encoder base64 = Base64.getEncoder();
        return String.join("$", SCHEME, String.valueOf(ITERATIONS),
                base64.encodeToString(salt), base64.encodeToString(derive(password, salt, ITERATIONS, KEY_BITS)));
    }

    /** Returns false for a wrong password or a stored value that is not in the format above. */
    public static boolean verify(String password, String stored) {
        String[] parts = stored == null ? new String[0] : stored.split("\\$");
        if (parts.length != 4 || !SCHEME.equals(parts[0])) {
            return false;
        }
        try {
            Base64.Decoder base64 = Base64.getDecoder();
            byte[] expected = base64.decode(parts[3]);
            byte[] actual = derive(password, base64.decode(parts[2]), Integer.parseInt(parts[1]), expected.length * 8);
            return MessageDigest.isEqual(expected, actual);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /** Takes as long as a real check, so an unknown username cannot be told apart by timing. */
    public static void dummyVerify(String password) {
        derive(password, DUMMY_SALT, ITERATIONS, KEY_BITS);
    }

    private static byte[] derive(String password, byte[] salt, int iterations, int keyBits) {
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, iterations, keyBits);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("PBKDF2 is not available", e);
        } finally {
            spec.clearPassword();
        }
    }
}
