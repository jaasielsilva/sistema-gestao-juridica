package com.example.app.auth.mfa;

import com.example.app.TestProps;
import com.example.app.config.AppProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SecretCipherTest {

    private SecretCipher cipher(String key) {
        AppProperties b = TestProps.app(true);
        return new SecretCipher(new AppProperties(b.baseUrl(), true, b.jwt(), b.refresh(), b.throttle(),
                new AppProperties.Mfa("T", key), b.mail()));
    }

    @Test
    void criptografaEDescriptografa() {
        SecretCipher c = cipher(TestProps.MFA_KEY);
        byte[] secret = "segredo-totp-123".getBytes();

        String enc = c.encrypt(secret);

        assertThat(enc).doesNotContain("segredo");
        assertThat(c.decrypt(enc)).isEqualTo(secret);
    }

    @Test
    void cadaCriptografiaUsaUmIvDiferente() {
        SecretCipher c = cipher(TestProps.MFA_KEY);
        assertThat(c.encrypt("x".getBytes())).isNotEqualTo(c.encrypt("x".getBytes()));
    }

    @Test
    void dadoAdulteradoOuChaveErradaFalha() {
        SecretCipher c = cipher(TestProps.MFA_KEY);
        String enc = c.encrypt("abc".getBytes());
        String tampered = enc.substring(0, enc.length() - 4) + "AAAA";

        assertThatThrownBy(() -> c.decrypt(tampered)).isInstanceOf(IllegalStateException.class);
        String otherKey = java.util.Base64.getEncoder().encodeToString(new byte[32]);
        assertThatThrownBy(() -> cipher(otherKey).decrypt(enc)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void chaveInvalidaImpedeAPartida() {
        assertThatThrownBy(() -> cipher("curta")).isInstanceOf(IllegalStateException.class);
        String key16 = java.util.Base64.getEncoder().encodeToString(new byte[16]);
        assertThatThrownBy(() -> cipher(key16)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 bytes");
    }
}
