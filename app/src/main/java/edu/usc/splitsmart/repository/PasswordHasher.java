package edu.usc.splitsmart.repository;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * Turns a password into a hash that can be stored, and checks a password against a stored hash.
 * Passwords are never stored or logged in plain text.
 *
 * <p>Uses PBKDF2 with a random salt per password, which is deliberately slow so that a stolen
 * database can't be brute forced quickly. Hashing takes a few hundred milliseconds, so it only
 * ever runs off the main thread.
 *
 * <p>Stored format: {@code pbkdf2$<iterations>$<salt>$<hash>}, salt and hash Base64 encoded. The
 * iteration count is part of the string so old hashes keep working if we raise it later.
 */
public final class PasswordHasher {

    private static final String PREFIX = "pbkdf2";
    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final int ITERATIONS = 100_000;
    private static final int KEY_LENGTH_BITS = 256;
    private static final int SALT_BYTES = 16;

    private static final SecureRandom RANDOM = new SecureRandom();

    private PasswordHasher() {
    }

    /** Hashes a password for storage. Two calls with the same password return different text. */
    public static String hash(String password) {
        byte[] salt = new byte[SALT_BYTES];
        RANDOM.nextBytes(salt);
        byte[] key = derive(password, salt, ITERATIONS);
        return PREFIX + "$" + ITERATIONS + "$" + encode(salt) + "$" + encode(key);
    }

    /** True if the password produced {@code storedHash}. False for anything unreadable. */
    public static boolean matches(String password, String storedHash) {
        String[] parts = storedHash.split("\\$");
        if (parts.length != 4 || !PREFIX.equals(parts[0])) {
            return false;
        }
        int iterations;
        byte[] salt;
        byte[] expected;
        try {
            iterations = Integer.parseInt(parts[1]);
            salt = Base64.getDecoder().decode(parts[2]);
            expected = Base64.getDecoder().decode(parts[3]);
        } catch (IllegalArgumentException unreadable) {
            return false;
        }
        // Compares every byte even after a mismatch, so the time taken gives nothing away.
        return MessageDigest.isEqual(derive(password, salt, iterations), expected);
    }

    private static byte[] derive(String password, byte[] salt, int iterations) {
        PBEKeySpec spec =
                new PBEKeySpec(password.toCharArray(), salt, iterations, KEY_LENGTH_BITS);
        try {
            return SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).getEncoded();
        } catch (NoSuchAlgorithmException | InvalidKeySpecException unavailable) {
            throw new IllegalStateException("PBKDF2 is missing on this device", unavailable);
        } finally {
            spec.clearPassword();
        }
    }

    private static String encode(byte[] bytes) {
        return Base64.getEncoder().encodeToString(bytes);
    }
}
