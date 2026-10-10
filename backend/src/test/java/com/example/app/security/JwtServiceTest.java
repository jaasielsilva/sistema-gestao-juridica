package com.example.app.security;

import com.example.app.user.Papel;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    private static final String SECRET = "unit-test-secret-unit-test-secret-1234";

    private JwtService service(String secret, String issuer, long minutes) {
        return new JwtService(new JwtProperties(secret, issuer, minutes));
    }

    @Test
    void tokenGeradoEValidoEPreservaIdentidade() {
        JwtService jwt = service(SECRET, "app", 5);
        String token = jwt.generate(42L, "a@b.com", Papel.ADMIN);

        var principal = jwt.parse(token);

        assertThat(principal).isPresent();
        assertThat(principal.get().userId()).isEqualTo(42L);
        assertThat(principal.get().email()).isEqualTo("a@b.com");
        assertThat(principal.get().papel()).isEqualTo(Papel.ADMIN);
    }

    @Test
    void tokenAdulteradoEhRejeitado() {
        JwtService jwt = service(SECRET, "app", 5);
        String token = jwt.generate(1L, "a@b.com", Papel.USER);

        assertThat(jwt.parse(token + "x")).isEmpty();
    }

    @Test
    void tokenAssinadoComOutraChaveEhRejeitado() {
        String token = service("another-secret-another-secret-12345678", "app", 5).generate(1L, "a@b.com", Papel.USER);

        assertThat(service(SECRET, "app", 5).parse(token)).isEmpty();
    }

    @Test
    void tokenDeOutroEmissorEhRejeitado() {
        String token = service(SECRET, "outro", 5).generate(1L, "a@b.com", Papel.USER);

        assertThat(service(SECRET, "app", 5).parse(token)).isEmpty();
    }

    @Test
    void tokenExpiradoEhRejeitado() {
        // Duracao negativa produz um token ja expirado.
        String token = service(SECRET, "app", -1).generate(1L, "a@b.com", Papel.USER);

        assertThat(service(SECRET, "app", 5).parse(token)).isEmpty();
    }

    @Test
    void lixoEhRejeitadoSemExcecao() {
        assertThat(service(SECRET, "app", 5).parse("nao-e-um-jwt")).isEmpty();
    }
}
