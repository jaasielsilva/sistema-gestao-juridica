package br.com.jaasielsilva.gestaojuridica.auth.mfa;

import br.com.jaasielsilva.gestaojuridica.shared.Hashing;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.OptionalLong;

/** TOTP conforme RFC 6238 (HMAC-SHA1, 6 digitos, passo de 30 s), compativel com Google/Microsoft Authenticator, Authy etc. */
@Component
public class TotpService {

    static final int PERIOD_SECONDS = 30;
    private static final int DIGITS = 6;
    private static final int SECRET_BYTES = 20;
    private static final char[] BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567".toCharArray();

    public byte[] newSecret() {
        return Hashing.randomBytes(SECRET_BYTES);
    }

    public long stepAt(Instant when) {
        return when.getEpochSecond() / PERIOD_SECONDS;
    }

    /**
     * Confere o codigo na janela de +-1 passo (tolera relogio um pouco fora). So aceita passos MAIORES que o ultimo
     * usado, entao o mesmo codigo nao vale duas vezes. Retorna o passo aceito.
     */
    public OptionalLong verify(byte[] secret, String code, Long lastUsedStep, Instant now) {
        if (code == null || !code.matches("\\d{6}")) {
            return OptionalLong.empty();
        }
        long current = stepAt(now);
        for (long step = current - 1; step <= current + 1; step++) {
            if (lastUsedStep != null && step <= lastUsedStep) {
                continue;
            }
            if (MessageDigest.isEqual(codeFor(secret, step).getBytes(StandardCharsets.UTF_8),
                    code.getBytes(StandardCharsets.UTF_8))) {
                return OptionalLong.of(step);
            }
        }
        return OptionalLong.empty();
    }

    /** Gera o codigo de um passo especifico (usado na verificacao e nos testes). */
    public String codeFor(byte[] secret, long step) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(secret, "HmacSHA1"));
            byte[] counter = new byte[8];
            for (int i = 7, s = 0; i >= 0; i--, s += 8) {
                counter[i] = (byte) (step >>> s);
            }
            byte[] hash = mac.doFinal(counter);
            int offset = hash[hash.length - 1] & 0x0F;
            int binary = ((hash[offset] & 0x7F) << 24) | ((hash[offset + 1] & 0xFF) << 16)
                    | ((hash[offset + 2] & 0xFF) << 8) | (hash[offset + 3] & 0xFF);
            return String.format("%0" + DIGITS + "d", binary % 1_000_000);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    public String base32(byte[] data) {
        StringBuilder out = new StringBuilder();
        int buffer = 0;
        int bits = 0;
        for (byte b : data) {
            buffer = (buffer << 8) | (b & 0xFF);
            bits += 8;
            while (bits >= 5) {
                out.append(BASE32[(buffer >> (bits - 5)) & 31]);
                bits -= 5;
            }
        }
        if (bits > 0) {
            out.append(BASE32[(buffer << (5 - bits)) & 31]);
        }
        return out.toString();
    }

    private static String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    public String otpauthUri(String issuer, String account, byte[] secret) {
        String label = enc(issuer) + ":" + enc(account);
        return "otpauth://totp/" + label + "?secret=" + base32(secret)
                + "&issuer=" + enc(issuer)
                + "&algorithm=SHA1&digits=" + DIGITS + "&period=" + PERIOD_SECONDS;
    }
}
