package br.com.jaasielsilva.gestaojuridica.security;

import br.com.jaasielsilva.gestaojuridica.config.AppProperties;
import br.com.jaasielsilva.gestaojuridica.user.Papel;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

/**
 * Emite e valida dois tipos de JWT, distintos pelo claim "typ":
 * "access" (curto, identifica usuario/tenant/sessao) e "mfa" (5 min, so serve para concluir o login com MFA).
 */
@Service
public class JwtService {

    private static final String TYPE_ACCESS = "access";
    private static final String TYPE_MFA = "mfa";
    private static final Duration MFA_TTL = Duration.ofMinutes(5);

    private final AppProperties.Jwt props;
    private final SecretKey key;

    public JwtService(AppProperties props) {
        this.props = props.jwt();
        this.key = Keys.hmacShaKeyFor(this.props.secret().getBytes(StandardCharsets.UTF_8));
    }

    public String generateAccess(AuthPrincipal p) {
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer(props.issuer())
                .subject(String.valueOf(p.userId()))
                .claim("typ", TYPE_ACCESS)
                .claim("email", p.email())
                .claim("papel", p.papel().name())
                .claim("tid", p.tenantId())
                .claim("sid", p.sessionId().toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(Duration.ofMinutes(props.accessMinutes()))))
                .signWith(key)
                .compact();
    }

    public long accessSeconds() {
        return Duration.ofMinutes(props.accessMinutes()).toSeconds();
    }

    /** Valida assinatura, emissor, validade e tipo. Token invalido resulta em Optional vazio (nunca excecao). */
    public Optional<AuthPrincipal> parseAccess(String token) {
        return claims(token, TYPE_ACCESS).map(c -> {
            try {
                return new AuthPrincipal(
                        Long.valueOf(c.getSubject()),
                        c.get("email", String.class),
                        Papel.valueOf(c.get("papel", String.class)),
                        ((Number) c.get("tid")).longValue(),
                        UUID.fromString(c.get("sid", String.class)));
            } catch (RuntimeException ex) {
                return null;
            }
        });
    }

    public String generateMfaToken(Long userId) {
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer(props.issuer())
                .subject(String.valueOf(userId))
                .claim("typ", TYPE_MFA)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(MFA_TTL)))
                .signWith(key)
                .compact();
    }

    public Optional<Long> parseMfaToken(String token) {
        return claims(token, TYPE_MFA).map(c -> {
            try {
                return Long.valueOf(c.getSubject());
            } catch (NumberFormatException ex) {
                return null;
            }
        });
    }

    private Optional<Claims> claims(String token, String expectedType) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .requireIssuer(props.issuer())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return expectedType.equals(claims.get("typ", String.class)) ? Optional.of(claims) : Optional.empty();
        } catch (JwtException | IllegalArgumentException ex) {
            return Optional.empty();
        }
    }
}
