package br.com.jaasielsilva.gestaojuridica.auth;

import br.com.jaasielsilva.gestaojuridica.config.AppProperties;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Cookie do refresh token: HttpOnly (JavaScript nao le), SameSite=Strict (nao viaja em requisicoes de outros sites)
 * e restrito ao caminho /api/v1/auth. Secure em producao (HTTPS).
 */
@Component
public class RefreshCookie {

    public static final String NAME = "refresh_token";
    private static final String PATH = "/api/v1/auth";

    private final boolean secure;

    public RefreshCookie(AppProperties props) {
        this.secure = props.refresh().cookieSecure();
    }

    public String issue(String value, Duration ttl) {
        return base(value).maxAge(ttl).build().toString();
    }

    public String clear() {
        return base("").maxAge(Duration.ZERO).build().toString();
    }

    private ResponseCookie.ResponseCookieBuilder base(String value) {
        return ResponseCookie.from(NAME, value).httpOnly(true).secure(secure).sameSite("Strict").path(PATH);
    }
}
