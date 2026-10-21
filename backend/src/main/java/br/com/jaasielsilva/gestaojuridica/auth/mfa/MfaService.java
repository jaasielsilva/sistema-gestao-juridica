package br.com.jaasielsilva.gestaojuridica.auth.mfa;

import br.com.jaasielsilva.gestaojuridica.audit.AuditService;
import br.com.jaasielsilva.gestaojuridica.audit.TipoEvento;
import br.com.jaasielsilva.gestaojuridica.auth.throttle.ThrottleService;
import br.com.jaasielsilva.gestaojuridica.config.AppProperties;
import br.com.jaasielsilva.gestaojuridica.shared.BusinessException;
import br.com.jaasielsilva.gestaojuridica.shared.Hashing;
import br.com.jaasielsilva.gestaojuridica.user.Usuario;
import br.com.jaasielsilva.gestaojuridica.user.UsuarioRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.OptionalLong;

@Service
public class MfaService {

    private static final int RECOVERY_CODES = 8;
    private static final String RECOVERY_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"; // sem 0/O/1/I

    private final UsuarioRepository usuarios;
    private final MfaRecoveryCodeRepository recoveryCodes;
    private final TotpService totp;
    private final SecretCipher cipher;
    private final PasswordEncoder encoder;
    private final AuditService audit;
    private final ThrottleService throttle;
    private final String issuer;

    public MfaService(UsuarioRepository usuarios, MfaRecoveryCodeRepository recoveryCodes, TotpService totp,
                      SecretCipher cipher, PasswordEncoder encoder, AuditService audit, ThrottleService throttle,
                      AppProperties props) {
        this.usuarios = usuarios;
        this.recoveryCodes = recoveryCodes;
        this.totp = totp;
        this.cipher = cipher;
        this.encoder = encoder;
        this.audit = audit;
        this.throttle = throttle;
        this.issuer = props.mfa().issuer();
    }

    public record Setup(String secret, String otpauthUri) {
    }

    /** Gera um segredo novo (ainda inativo). O MFA so passa a valer depois de confirmar um codigo em enable(). */
    @Transactional
    public Setup setup(Long userId) {
        Usuario u = load(userId);
        if (u.isMfaAtivo()) {
            throw BusinessException.conflict("O MFA ja esta ativo. Desative-o antes de configurar de novo.");
        }
        byte[] secret = totp.newSecret();
        u.iniciarConfiguracaoMfa(cipher.encrypt(secret));
        return new Setup(totp.base32(secret), totp.otpauthUri(issuer, u.getEmail(), secret));
    }

    /** Confirma o primeiro codigo, ativa o MFA e devolve os codigos de recuperacao (mostrados uma unica vez). */
    @Transactional
    public List<String> enable(Long userId, String code) {
        String key = ThrottleService.userKey("mfa", userId);
        throttle.assertNotBlocked(key);
        Usuario u = load(userId);
        if (u.isMfaAtivo() || u.getMfaSegredo() == null) {
            throw BusinessException.conflict("Inicie a configuracao do MFA primeiro.");
        }
        OptionalLong step = totp.verify(cipher.decrypt(u.getMfaSegredo()), code, null, Instant.now());
        if (step.isEmpty()) {
            throttle.hit(key, throttle.maxLoginPerEmail());
            audit.falha(TipoEvento.MFA_FALHA, u.getId(), u.getTenantId(), null, "confirmacao do MFA");
            throw BusinessException.badRequest("Codigo invalido");
        }
        u.ativarMfa(step.getAsLong());
        List<String> codes = generateRecoveryCodes(u.getId());
        audit.sucesso(TipoEvento.MFA_HABILITADO, u.getId(), u.getTenantId(), null);
        return codes;
    }

    /** Exige senha E um codigo valido (TOTP ou de recuperacao) para desativar. */
    @Transactional
    public void disable(Long userId, String senha, String code) {
        String key = ThrottleService.userKey("mfa", userId);
        throttle.assertNotBlocked(key);
        Usuario u = load(userId);
        if (!u.isMfaAtivo()) {
            throw BusinessException.conflict("O MFA nao esta ativo.");
        }
        if (!encoder.matches(senha, u.getSenhaHash()) || !verifyCode(u, code)) {
            throttle.hit(key, throttle.maxLoginPerEmail());
            audit.falha(TipoEvento.MFA_FALHA, u.getId(), u.getTenantId(), null, "tentativa de desativar o MFA");
            throw BusinessException.unauthorized("Senha ou codigo invalido");
        }
        u.desativarMfa();
        recoveryCodes.apagarDoUsuario(u.getId());
        audit.sucesso(TipoEvento.MFA_DESABILITADO, u.getId(), u.getTenantId(), null);
    }

    /** Confere TOTP (com protecao contra replay) ou codigo de recuperacao (uso unico). Deve rodar numa transacao. */
    public boolean verifyCode(Usuario u, String code) {
        if (code == null || u.getMfaSegredo() == null) {
            return false;
        }
        String normalized = code.trim();
        OptionalLong step = totp.verify(cipher.decrypt(u.getMfaSegredo()), normalized, u.getMfaUltimoPasso(), Instant.now());
        if (step.isPresent()) {
            u.registrarPassoMfa(step.getAsLong());
            return true;
        }
        String recovery = normalized.toUpperCase(Locale.ROOT).replace("-", "").replace(" ", "");
        if (recovery.length() == 10
                && recoveryCodes.consumir(u.getId(), Hashing.sha256Hex(recovery), Instant.now()) == 1) {
            audit.sucesso(TipoEvento.MFA_CODIGO_RECUPERACAO_USADO, u.getId(), u.getTenantId(), null);
            return true;
        }
        return false;
    }

    private List<String> generateRecoveryCodes(Long userId) {
        recoveryCodes.apagarDoUsuario(userId);
        List<String> shown = new ArrayList<>();
        for (int i = 0; i < RECOVERY_CODES; i++) {
            StringBuilder raw = new StringBuilder();
            for (int c = 0; c < 10; c++) {
                raw.append(RECOVERY_ALPHABET.charAt(Hashing.randomInt(RECOVERY_ALPHABET.length())));
            }
            recoveryCodes.save(new MfaRecoveryCode(userId, Hashing.sha256Hex(raw.toString())));
            shown.add(raw.substring(0, 5) + "-" + raw.substring(5));
        }
        return shown;
    }

    private Usuario load(Long userId) {
        return usuarios.findById(userId).orElseThrow(() -> BusinessException.unauthorized("Usuario nao encontrado"));
    }
}
