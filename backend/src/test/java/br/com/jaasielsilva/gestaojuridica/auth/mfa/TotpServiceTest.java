package br.com.jaasielsilva.gestaojuridica.auth.mfa;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class TotpServiceTest {

    private final TotpService totp = new TotpService();
    // Segredo e instantes do vetor de teste da RFC 6238 (SHA-1).
    private static final byte[] RFC_SECRET = "12345678901234567890".getBytes(StandardCharsets.US_ASCII);

    @Test
    void geraOsCodigosDoVetorDeTesteDaRfc6238() {
        // RFC 6238, apendice B (8 digitos): t=59 -> 94287082; t=1111111109 -> 07081804. Aqui sao os 6 ultimos digitos.
        assertThat(totp.codeFor(RFC_SECRET, 59 / 30)).isEqualTo("287082");
        assertThat(totp.codeFor(RFC_SECRET, 1111111109L / 30)).isEqualTo("081804");
        assertThat(totp.codeFor(RFC_SECRET, 1234567890L / 30)).isEqualTo("005924");
    }

    @Test
    void aceitaCodigoDoPassoAtualEDosVizinhos() {
        Instant now = Instant.ofEpochSecond(1_700_000_000L);
        long step = totp.stepAt(now);

        assertThat(totp.verify(RFC_SECRET, totp.codeFor(RFC_SECRET, step), null, now)).hasValue(step);
        assertThat(totp.verify(RFC_SECRET, totp.codeFor(RFC_SECRET, step - 1), null, now)).hasValue(step - 1);
        assertThat(totp.verify(RFC_SECRET, totp.codeFor(RFC_SECRET, step + 1), null, now)).hasValue(step + 1);
    }

    @Test
    void rejeitaCodigoForaDaJanela() {
        Instant now = Instant.ofEpochSecond(1_700_000_000L);
        long step = totp.stepAt(now);

        assertThat(totp.verify(RFC_SECRET, totp.codeFor(RFC_SECRET, step + 5), null, now)).isEmpty();
        assertThat(totp.verify(RFC_SECRET, totp.codeFor(RFC_SECRET, step - 5), null, now)).isEmpty();
    }

    @Test
    void naoAceitaOMesmoCodigoDuasVezes() {
        Instant now = Instant.ofEpochSecond(1_700_000_000L);
        long step = totp.stepAt(now);
        String code = totp.codeFor(RFC_SECRET, step);

        long used = totp.verify(RFC_SECRET, code, null, now).getAsLong();

        assertThat(totp.verify(RFC_SECRET, code, used, now)).isEmpty();
    }

    @Test
    void rejeitaFormatoInvalido() {
        Instant now = Instant.now();
        assertThat(totp.verify(RFC_SECRET, null, null, now)).isEmpty();
        assertThat(totp.verify(RFC_SECRET, "12345", null, now)).isEmpty();
        assertThat(totp.verify(RFC_SECRET, "abcdef", null, now)).isEmpty();
    }

    @Test
    void base32EhCompativelComOsAppsAutenticadores() {
        // Vetor do RFC 4648: "foobar" -> MZXW6YTBOI (sem padding).
        assertThat(totp.base32("foobar".getBytes(StandardCharsets.US_ASCII))).isEqualTo("MZXW6YTBOI");
        assertThat(totp.base32(RFC_SECRET)).isEqualTo("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ");
    }

    @Test
    void uriOtpauthContemIssuerESegredo() {
        String uri = totp.otpauthUri("Minha App", "ana@x.com", RFC_SECRET);

        assertThat(uri).startsWith("otpauth://totp/Minha%20App:ana%40x.com?secret=GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ")
                .contains("issuer=Minha%20App", "digits=6", "period=30");
    }
}
