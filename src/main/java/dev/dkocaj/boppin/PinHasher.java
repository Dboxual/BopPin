package dev.dkocaj.boppin;

import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.util.HexFormat;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

final class PinHasher {
    private static final String ALGO = "PBKDF2WithHmacSHA256";
    private static final int ITERATIONS = 120_000;
    private static final int KEY_BITS = 256;
    private static final int SALT_BYTES = 16;

    private static final SecureRandom RNG = new SecureRandom();
    private static final HexFormat HEX = HexFormat.of();

    private PinHasher() {}

    static String hash(String pin) {
        byte[] salt = new byte[SALT_BYTES];
        RNG.nextBytes(salt);
        byte[] derived = derive(pin.toCharArray(), salt);
        return ITERATIONS + "$" + HEX.formatHex(salt) + "$" + HEX.formatHex(derived);
    }

    static boolean verify(String pin, String stored) {
        String[] parts = stored.split("\\$");
        if (parts.length != 3) return false;
        int iter;
        byte[] salt;
        byte[] expected;
        try {
            iter = Integer.parseInt(parts[0]);
            salt = HEX.parseHex(parts[1]);
            expected = HEX.parseHex(parts[2]);
        } catch (RuntimeException e) {
            return false;
        }
        byte[] actual = derive(pin.toCharArray(), salt, iter, expected.length * 8);
        return constantTimeEquals(expected, actual);
    }

    private static byte[] derive(char[] pin, byte[] salt) {
        return derive(pin, salt, ITERATIONS, KEY_BITS);
    }

    private static byte[] derive(char[] pin, byte[] salt, int iter, int bits) {
        try {
            KeySpec spec = new PBEKeySpec(pin, salt, iter, bits);
            SecretKeyFactory f = SecretKeyFactory.getInstance(ALGO);
            return f.generateSecret(spec).getEncoded();
        } catch (Exception e) {
            throw new IllegalStateException("PIN hashing failed", e);
        }
    }

    private static boolean constantTimeEquals(byte[] a, byte[] b) {
        if (a.length != b.length) return false;
        int diff = 0;
        for (int i = 0; i < a.length; i++) diff |= a[i] ^ b[i];
        return diff == 0;
    }
}
