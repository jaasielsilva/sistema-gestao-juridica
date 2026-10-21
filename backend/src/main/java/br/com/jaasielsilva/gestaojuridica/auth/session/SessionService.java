package br.com.jaasielsilva.gestaojuridica.auth.session;

import br.com.jaasielsilva.gestaojuridica.audit.AuditService;
import br.com.jaasielsilva.gestaojuridica.audit.TipoEvento;
import br.com.jaasielsilva.gestaojuridica.config.AppProperties;
import br.com.jaasielsilva.gestaojuridica.security.AuthPrincipal;
import br.com.jaasielsilva.gestaojuridica.security.JwtService;
import br.com.jaasielsilva.gestaojuridica.shared.BusinessException;
import br.com.jaasielsilva.gestaojuridica.shared.Hashing;
import br.com.jaasielsilva.gestaojuridica.shared.RequestInfo;
import br.com.jaasielsilva.gestaojuridica.user.Usuario;
import br.com.jaasielsilva.gestaojuridica.user.UsuarioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Sessoes e refresh tokens.
 * - Cada login cria uma sessao; o access token carrega o id dela e e invalidado quando ela e revogada.
 * - O refresh token e opaco, de uso unico e rotacionado a cada uso. Reapresentar um refresh token JA usado
 *   (alem de uma pequena tolerancia para abas concorrentes) indica roubo: a sessao inteira e revogada.
 */
@Service
public class SessionService {

    private final SessaoRepository sessoes;
    private final RefreshTokenRepository refreshTokens;
    private final UsuarioRepository usuarios;
    private final JwtService jwt;
    private final AuditService audit;
    private final RequestInfo request;
    private final Duration refreshTtl;
    /** Tolerancia para duas abas do mesmo navegador renovarem ao mesmo tempo com o mesmo cookie. */
    private final Duration reuseGrace;

    public SessionService(SessaoRepository sessoes, RefreshTokenRepository refreshTokens, UsuarioRepository usuarios,
                          JwtService jwt, AuditService audit, RequestInfo request, AppProperties props) {
        this.sessoes = sessoes;
        this.refreshTokens = refreshTokens;
        this.usuarios = usuarios;
        this.jwt = jwt;
        this.audit = audit;
        this.request = request;
        this.refreshTtl = Duration.ofDays(props.refresh().days());
        this.reuseGrace = Duration.ofSeconds(props.refresh().reuseGraceSeconds());
    }

    public record IssuedTokens(String accessToken, long expiresInSeconds, String refreshToken, Duration refreshTtl) {
    }

    @Transactional
    public IssuedTokens start(Usuario u) {
        Instant now = Instant.now();
        Sessao s = sessoes.save(new Sessao(u.getId(), now.plus(refreshTtl), request.ip(), request.userAgent()));
        return issue(u, s, now);
    }

    /** Troca um refresh token valido por um novo par de tokens (rotacao). */
    @Transactional(noRollbackFor = BusinessException.class)
    public IssuedTokens rotate(String rawRefresh) {
        if (rawRefresh == null || rawRefresh.isBlank()) {
            throw BusinessException.unauthorized("Sessao expirada");
        }
        Instant now = Instant.now();
        RefreshToken token = refreshTokens.findByTokenHash(Hashing.sha256Hex(rawRefresh))
                .orElseThrow(() -> BusinessException.unauthorized("Sessao expirada"));
        Sessao sessao = sessoes.findById(token.getSessaoId())
                .orElseThrow(() -> BusinessException.unauthorized("Sessao expirada"));
        Usuario user = usuarios.findById(sessao.getUsuarioId()).orElse(null);

        if (!sessao.estaAtiva(now) || token.getExpiraEm().isBefore(now) || user == null || !user.isAtivo()) {
            throw BusinessException.unauthorized("Sessao expirada");
        }
        if (token.getUsadoEm() != null) {
            if (token.getUsadoEm().isBefore(now.minus(reuseGrace))) {
                sessao.revogar(now);
                audit.falha(TipoEvento.REFRESH_REUSO, user.getId(), user.getTenantId(), null,
                        "refresh token reutilizado: sessao revogada");
                throw BusinessException.unauthorized("Sessao expirada");
            }
            // Dentro do grace period: tolerancia para abas concorrentes (ADR 0004).
            // Auditado como aviso para deteccao de anomalias (pico = possivel roubo de cookie).
            audit.falha(TipoEvento.REFRESH_REUSO_GRACE, user.getId(), user.getTenantId(), null,
                    "refresh token reutilizado dentro do grace period");
        } else {
            token.marcarUsado(now);
        }
        sessao.estenderAte(now.plus(refreshTtl));
        return issue(user, sessao, now);
    }

    /** Revoga a sessao dona do refresh token informado. Retorna o usuario, se havia sessao. */
    @Transactional
    public Optional<Long> revokeByRefresh(String rawRefresh) {
        if (rawRefresh == null || rawRefresh.isBlank()) {
            return Optional.empty();
        }
        return refreshTokens.findByTokenHash(Hashing.sha256Hex(rawRefresh))
                .flatMap(t -> sessoes.findById(t.getSessaoId()))
                .map(s -> {
                    s.revogar(Instant.now());
                    return s.getUsuarioId();
                });
    }

    @Transactional
    public void revoke(UUID sessionId) {
        sessoes.findById(sessionId).ifPresent(s -> s.revogar(Instant.now()));
    }

    @Transactional
    public void revokeAll(Long userId) {
        sessoes.revogarTodasDoUsuario(userId, Instant.now());
    }

    @Transactional
    public void revokeOthers(Long userId, UUID keep) {
        sessoes.revogarOutrasDoUsuario(userId, keep, Instant.now());
    }

    private IssuedTokens issue(Usuario u, Sessao s, Instant now) {
        String raw = Hashing.randomToken(32);
        refreshTokens.save(new RefreshToken(s.getId(), Hashing.sha256Hex(raw), now.plus(refreshTtl)));
        String access = jwt.generateAccess(new AuthPrincipal(u.getId(), u.getEmail(), u.getPapel(), u.getTenantId(), s.getId()));
        return new IssuedTokens(access, jwt.accessSeconds(), raw, refreshTtl);
    }
}
