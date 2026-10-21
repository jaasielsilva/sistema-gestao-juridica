package br.com.jaasielsilva.gestaojuridica.auth;

import br.com.jaasielsilva.gestaojuridica.audit.AuditService;
import br.com.jaasielsilva.gestaojuridica.audit.TipoEvento;
import br.com.jaasielsilva.gestaojuridica.auth.session.SessionService;
import br.com.jaasielsilva.gestaojuridica.auth.throttle.ThrottleService;
import br.com.jaasielsilva.gestaojuridica.auth.token.TipoToken;
import br.com.jaasielsilva.gestaojuridica.auth.token.TokenAcaoService;
import br.com.jaasielsilva.gestaojuridica.mail.AccountMailer;
import br.com.jaasielsilva.gestaojuridica.security.AuthPrincipal;
import br.com.jaasielsilva.gestaojuridica.shared.BusinessException;
import br.com.jaasielsilva.gestaojuridica.shared.RequestInfo;
import br.com.jaasielsilva.gestaojuridica.user.Usuario;
import br.com.jaasielsilva.gestaojuridica.user.UsuarioRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;

/** Fluxos de conta: verificacao de e-mail, recuperacao e troca de senha. */
@Service
public class AccountService {

    private static final String INVALID_LINK = "Link invalido ou expirado";

    private final UsuarioRepository usuarios;
    private final PasswordEncoder encoder;
    private final TokenAcaoService tokens;
    private final SessionService sessions;
    private final ThrottleService throttle;
    private final AuditService audit;
    private final RequestInfo request;
    private final AccountMailer mailer;

    public AccountService(UsuarioRepository usuarios, PasswordEncoder encoder, TokenAcaoService tokens,
                          SessionService sessions, ThrottleService throttle, AuditService audit,
                          RequestInfo request, AccountMailer mailer) {
        this.usuarios = usuarios;
        this.encoder = encoder;
        this.tokens = tokens;
        this.sessions = sessions;
        this.throttle = throttle;
        this.audit = audit;
        this.request = request;
        this.mailer = mailer;
    }

    @Transactional
    public void verifyEmail(String token) {
        Long userId = tokens.consumir(token, TipoToken.VERIFICACAO_EMAIL)
                .orElseThrow(() -> BusinessException.badRequest(INVALID_LINK));
        Usuario u = usuarios.findById(userId).orElseThrow(() -> BusinessException.badRequest(INVALID_LINK));
        u.marcarEmailVerificado();
        audit.sucesso(TipoEvento.EMAIL_VERIFICADO, u.getId(), u.getTenantId(), null);
    }

    /** Sempre responde igual, exista a conta ou nao (nao revela quais e-mails estao cadastrados). */
    @Transactional
    public void resendVerification(String rawEmail) {
        String email = AuthService.normalize(rawEmail);
        throttle.consume(ThrottleService.ipKey("resend", request.ip()), throttle.maxSensitivePerIp());
        throttle.consume(ThrottleService.emailKey("resend", email), 3);
        usuarios.findByEmail(email).filter(u -> u.isAtivo() && !u.isEmailVerificado()).ifPresent(u -> {
            String token = tokens.emitir(u.getId(), TipoToken.VERIFICACAO_EMAIL, Duration.ofHours(24));
            mailer.sendVerification(u.getEmail(), u.getNome(), token);
        });
    }

    /** Sempre responde igual, exista a conta ou nao. */
    @Transactional
    public void forgotPassword(String rawEmail) {
        String email = AuthService.normalize(rawEmail);
        throttle.consume(ThrottleService.ipKey("forgot", request.ip()), throttle.maxSensitivePerIp());
        throttle.consume(ThrottleService.emailKey("forgot", email), 3);
        usuarios.findByEmail(email).filter(Usuario::isAtivo).ifPresent(u -> {
            String token = tokens.emitir(u.getId(), TipoToken.RESET_SENHA, Duration.ofHours(1));
            mailer.sendPasswordReset(u.getEmail(), u.getNome(), token);
            audit.sucesso(TipoEvento.SENHA_RESET_SOLICITADO, u.getId(), u.getTenantId(), null);
        });
    }

    @Transactional
    public void resetPassword(String token, String novaSenha) {
        throttle.consume(ThrottleService.ipKey("reset", request.ip()), throttle.maxSensitivePerIp());
        Long userId = tokens.consumir(token, TipoToken.RESET_SENHA)
                .orElseThrow(() -> BusinessException.badRequest(INVALID_LINK));
        Usuario u = usuarios.findById(userId).filter(Usuario::isAtivo)
                .orElseThrow(() -> BusinessException.badRequest(INVALID_LINK));
        u.definirSenhaHash(encoder.encode(novaSenha));
        u.marcarEmailVerificado(); // quem recebeu o link no e-mail provou ter acesso a ele
        sessions.revokeAll(u.getId()); // derruba todos os dispositivos: a senha antiga pode estar comprometida
        audit.sucesso(TipoEvento.SENHA_REDEFINIDA, u.getId(), u.getTenantId(), null);
    }

    @Transactional
    public void changePassword(AuthPrincipal principal, String senhaAtual, String novaSenha) {
        String key = ThrottleService.userKey("pwd", principal.userId());
        throttle.assertNotBlocked(key);
        Usuario u = usuarios.findById(principal.userId()).filter(Usuario::isAtivo)
                .orElseThrow(() -> BusinessException.unauthorized("Usuario nao encontrado"));
        if (!encoder.matches(senhaAtual, u.getSenhaHash())) {
            throttle.hit(key, throttle.maxLoginPerEmail());
            throw BusinessException.badRequest("Senha atual incorreta");
        }
        u.definirSenhaHash(encoder.encode(novaSenha));
        sessions.revokeOthers(u.getId(), principal.sessionId()); // mantem so esta sessao
        audit.sucesso(TipoEvento.SENHA_ALTERADA, u.getId(), u.getTenantId(), null);
    }
}
