package com.example.app.security;

import com.example.app.TestProps;
import com.example.app.config.AppProperties;
import com.example.app.user.Papel;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    private static final String SECRET = "unit-test-secret-unit-test-secret-1234";

    private JwtService service(String secret, String issuer, long minutes) {
        AppProperties base = TestProps.app(true);
        return new JwtService(new AppProperties(base.baseUrl(), true, new AppProperties.Jwt(secret, issuer, minutes),
                base.refresh(), base.throttle(), base.mfa(), base.mail()));
    }

    private AuthPrincipal principal(Papel papel) {
        return new AuthPrincipal(42L, "a@b.com", papel, 7L, UUID.randomUUID());
    }

    @Test
    void accessTokenPreservaIdentidadeTenantESessao() {
        JwtService jwt = service(SECRET, "app", 5);
        AuthPrincipal p = principal(Papel.ADMIN);

        var parsed = jwt.parseAccess(jwt.generateAccess(p));

        assertThat(parsed).contains(p);
    }

    @Test
    void tokenAdulteradoEhRejeitado() {
        JwtService jwt = service(SECRET, "app", 5);
        assertThat(jwt.parseAccess(jwt.generateAccess(principal(Papel.USER)) + "x")).isEmpty();
    }

    @Test
    void tokenAssinadoComOutraChaveEhRejeitado() {
        String token = service("another-secret-another-secret-12345678", "app", 5).generateAccess(principal(Papel.USER));
        assertThat(service(SECRET, "app", 5).parseAccess(token)).isEmpty();
    }

    @Test
    void tokenDeOutroEmissorEhRejeitado() {
        String token = service(SECRET, "outro", 5).generateAccess(principal(Papel.USER));
        assertThat(service(SECRET, "app", 5).parseAccess(token)).isEmpty();
    }

    @Test
    void tokenExpiradoEhRejeitado() {
        String token = service(SECRET, "app", -1).generateAccess(principal(Papel.USER));
        assertThat(service(SECRET, "app", 5).parseAccess(token)).isEmpty();
    }

    @Test
    void lixoEhRejeitadoSemExcecao() {
        assertThat(service(SECRET, "app", 5).parseAccess("nao-e-um-jwt")).isEmpty();
    }

    @Test
    void tokenMfaNaoServeComoAccessTokenENemViceVersa() {
        JwtService jwt = service(SECRET, "app", 5);

        String mfa = jwt.generateMfaToken(42L);
        String access = jwt.generateAccess(principal(Papel.USER));

        assertThat(jwt.parseAccess(mfa)).isEmpty();
        assertThat(jwt.parseMfaToken(access)).isEmpty();
        assertThat(jwt.parseMfaToken(mfa)).contains(42L);
    }
}
