package br.com.jaasielsilva.gestaojuridica.auth;

import br.com.jaasielsilva.gestaojuridica.audit.AuditService;
import br.com.jaasielsilva.gestaojuridica.audit.TipoEvento;
import br.com.jaasielsilva.gestaojuridica.auth.AuthDtos.LoginRequest;
import br.com.jaasielsilva.gestaojuridica.auth.AuthDtos.RegisterRequest;
import br.com.jaasielsilva.gestaojuridica.auth.AuthDtos.UserResponse;
import br.com.jaasielsilva.gestaojuridica.auth.mfa.MfaService;
import br.com.jaasielsilva.gestaojuridica.auth.session.SessionService;
import br.com.jaasielsilva.gestaojuridica.auth.session.SessionService.IssuedTokens;
import br.com.jaasielsilva.gestaojuridica.auth.throttle.ThrottleService;
import br.com.jaasielsilva.gestaojuridica.auth.token.TipoToken;
import br.com.jaasielsilva.gestaojuridica.auth.token.TokenAcaoService;
import br.com.jaasielsilva.gestaojuridica.config.AppProperties;
import br.com.jaasielsilva.gestaojuridica.mail.AccountMailer;
import br.com.jaasielsilva.gestaojuridica.security.AuthPrincipal;
import br.com.jaasielsilva.gestaojuridica.security.JwtService;
import br.com.jaasielsilva.gestaojuridica.shared.BusinessException;
import br.com.jaasielsilva.gestaojuridica.shared.RequestInfo;
import br.com.jaasielsilva.gestaojuridica.user.Usuario;
import br.com.jaasielsilva.gestaojuridica.user.UsuarioRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.Locale;
import java.util.UUID;

@Service
public class AuthService {

    private final UsuarioRepository usuarios;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final SessionService sessions;
    private final ThrottleService throttle;
    private final AuditService audit;
    private final RequestInfo request;
    private final MfaService mfa;
    private final TokenAcaoService tokens;
    private final AccountMailer mailer;
    private final AppProperties props;
    private final RegistrationWriter registrationWriter;
    // Hash descartavel: gasta o mesmo tempo de BCrypt quando o e-mail nao existe (evita enumeracao por timing).
    private final String dummyHash;

    public AuthService(UsuarioRepository usuarios, PasswordEncoder encoder, JwtService jwt, SessionService sessions,
                       ThrottleService throttle, AuditService audit, RequestInfo request, MfaService mfa,
                       TokenAcaoService tokens, AccountMailer mailer, AppProperties props,
                       RegistrationWriter registrationWriter) {
        this.usuarios = usuarios;
        this.encoder = encoder;
        this.jwt = jwt;
        this.sessions = sessions;
        this.throttle = throttle;
        this.audit = audit;
        this.request = request;
        this.mfa = mfa;
        this.tokens = tokens;
        this.mailer = mailer;
        this.props = props;
        this.registrationWriter = registrationWriter;
        this.dummyHash = encoder.encode(UUID.randomUUID().toString());
    }

    /** Resultado do login: tokens prontos OU a exigencia do segundo fator. */
    public record LoginOutcome(IssuedTokens tokens, String mfaToken) {
        public boolean mfaRequired() {
            return mfaToken != null;
        }
    }

    public void register(RegisterRequest req) {
        if (!props.registrationEnabled()) {
            throw BusinessException.forbidden("O cadastro esta desabilitado");
        }
        throttle.consume(ThrottleService.ipKey("register", request.ip()), throttle.maxSensitivePerIp());

        String email = normalize(req.email());
        if (usuarios.existsByEmail(email)) {
            encoder.matches(req.senha(), dummyHash);
            return;
        }
        Usuario u;
        try {
            u = registrationWriter.create(req, email, encoder.encode(req.senha()));
        } catch (DataIntegrityViolationException ex) {
            // A unique constraint arbitrates concurrent signups. Keep duplicate-email responses generic too.
            if (!usuarios.existsByEmail(email)) {
                throw ex;
            }
            encoder.matches(req.senha(), dummyHash);
            return;
        }
        String token = tokens.emitir(u.getId(), TipoToken.VERIFICACAO_EMAIL, Duration.ofHours(24));
        mailer.sendVerification(u.getEmail(), u.getNome(), token);
        audit.sucesso(TipoEvento.CADASTRO, u.getId(), u.getTenantId(), null);
    }

    @Transactional
    public LoginOutcome login(LoginRequest req) {
        String email = normalize(req.email());
        String emailKey = ThrottleService.emailKey("login", email);
        String ipKey = ThrottleService.ipKey("login", request.ip());
        throttle.assertNotBlocked(emailKey, ipKey);

        Usuario u = usuarios.findByEmail(email).orElse(null);
        boolean passwordOk = encoder.matches(req.senha(), u != null ? u.getSenhaHash() : dummyHash);
        // Mensagem unica para e-mail inexistente, senha errada ou usuario inativo (nao revela qual).
        if (u == null || !passwordOk || !u.isAtivo()) {
            throttle.hit(emailKey, throttle.maxLoginPerEmail());
            throttle.hit(ipKey, throttle.maxLoginPerIp());
            audit.falha(TipoEvento.LOGIN_FALHA, u != null ? u.getId() : null, u != null ? u.getTenantId() : null,
                    email, "credenciais invalidas");
            throw BusinessException.unauthorized("Credenciais invalidas");
        }
        if (!u.isEmailVerificado()) {
            audit.falha(TipoEvento.LOGIN_FALHA, u.getId(), u.getTenantId(), email, "e-mail nao verificado");
            throw BusinessException.forbidden("Confirme seu e-mail antes de entrar");
        }
        if (u.isMfaAtivo()) {
            audit.sucesso(TipoEvento.LOGIN_MFA_PENDENTE, u.getId(), u.getTenantId(), "senha correta, aguardando MFA");
            return new LoginOutcome(null, jwt.generateMfaToken(u.getId()));
        }
        throttle.reset(emailKey);
        return complete(u);
    }

    /** Segundo passo do login quando o MFA esta ativo. */
    @Transactional
    public LoginOutcome verifyMfa(String mfaToken, String codigo) {
        Long userId = jwt.parseMfaToken(mfaToken)
                .orElseThrow(() -> BusinessException.unauthorized("Verificacao expirada. Entre novamente."));
        String userKey = ThrottleService.userKey("mfa", userId);
        String ipKey = ThrottleService.ipKey("mfa", request.ip());
        throttle.assertNotBlocked(userKey, ipKey);

        Usuario u = usuarios.findById(userId).filter(Usuario::isAtivo)
                .orElseThrow(() -> BusinessException.unauthorized("Verificacao expirada. Entre novamente."));
        if (!u.isMfaAtivo() || !mfa.verifyCode(u, codigo)) {
            throttle.hit(userKey, throttle.maxLoginPerEmail());
            throttle.hit(ipKey, throttle.maxLoginPerIp());
            audit.falha(TipoEvento.MFA_FALHA, u.getId(), u.getTenantId(), null, "codigo MFA invalido no login");
            throw BusinessException.unauthorized("Codigo invalido");
        }
        throttle.reset(userKey);
        throttle.reset(ThrottleService.emailKey("login", u.getEmail()));
        return complete(u);
    }

    /** Renova o par de tokens a partir do cookie de refresh. */
    public IssuedTokens refresh(String rawRefreshToken) {
        return sessions.rotate(rawRefreshToken);
    }

    /** Encerra a sessao do cookie de refresh e/ou a do access token atual. Idempotente. */
    public void logout(String rawRefreshToken, AuthPrincipal principal) {
        var fromCookie = sessions.revokeByRefresh(rawRefreshToken);
        if (principal != null) {
            sessions.revoke(principal.sessionId());
        }
        Long userId = principal != null ? principal.userId() : fromCookie.orElse(null);
        if (userId != null) {
            audit.sucesso(TipoEvento.LOGOUT, userId, principal != null ? principal.tenantId() : null, null);
        }
    }

    public void logoutAll(AuthPrincipal principal) {
        sessions.revokeAll(principal.userId());
        audit.sucesso(TipoEvento.LOGOUT_TODOS, principal.userId(), principal.tenantId(), null);
    }

    @Transactional(readOnly = true)
    public UserResponse me(Long userId) {
        return usuarios.findById(userId)
                .filter(Usuario::isAtivo)
                .map(AuthService::toResponse)
                .orElseThrow(() -> BusinessException.unauthorized("Usuario nao encontrado"));
    }

    private LoginOutcome complete(Usuario u) {
        IssuedTokens issued = sessions.start(u);
        audit.sucesso(TipoEvento.LOGIN_SUCESSO, u.getId(), u.getTenantId(), null);
        return new LoginOutcome(issued, null);
    }

    static String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    static UserResponse toResponse(Usuario u) {
        return new UserResponse(u.getId(), u.getNome(), u.getEmail(), u.getPapel(), u.getTenantId(),
                u.isEmailVerificado(), u.isMfaAtivo());
    }
}
