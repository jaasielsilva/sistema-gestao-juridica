package br.com.jaasielsilva.gestaojuridica.shared;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

public final class Hashing {

    private static final SecureRandom RNG = new SecureRandom();

    private Hashing() {
    }

    /** SHA-256 em hexadecimal (64 chars). Usado para guardar tokens: nunca o valor original. */
    public static String sha256Hex(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Token aleatorio criptograficamente seguro, em base64url sem padding. */
    public static String randomToken(int bytes) {
        byte[] buf = new byte[bytes];
        RNG.nextBytes(buf);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
    }

    public static byte[] randomBytes(int bytes) {
        byte[] buf = new byte[bytes];
        RNG.nextBytes(buf);
        return buf;
    }

    public static int randomInt(int bound) {
        return RNG.nextInt(bound);
    }
}
