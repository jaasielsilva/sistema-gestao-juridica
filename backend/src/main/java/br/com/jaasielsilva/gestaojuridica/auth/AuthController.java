package br.com.jaasielsilva.gestaojuridica.auth;

import br.com.jaasielsilva.gestaojuridica.auth.AuthDtos.AuthConfigResponse;
import br.com.jaasielsilva.gestaojuridica.auth.AuthDtos.ChangePasswordRequest;
import br.com.jaasielsilva.gestaojuridica.auth.AuthDtos.EmailRequest;
import br.com.jaasielsilva.gestaojuridica.auth.AuthDtos.LoginRequest;
import br.com.jaasielsilva.gestaojuridica.auth.AuthDtos.LoginResponse;
import br.com.jaasielsilva.gestaojuridica.auth.AuthDtos.MessageResponse;
import br.com.jaasielsilva.gestaojuridica.auth.AuthDtos.MfaDisableRequest;
import br.com.jaasielsilva.gestaojuridica.auth.AuthDtos.MfaEnableRequest;
import br.com.jaasielsilva.gestaojuridica.auth.AuthDtos.MfaSetupResponse;
import br.com.jaasielsilva.gestaojuridica.auth.AuthDtos.MfaVerifyRequest;
import br.com.jaasielsilva.gestaojuridica.auth.AuthDtos.RecoveryCodesResponse;
import br.com.jaasielsilva.gestaojuridica.auth.AuthDtos.RegisterRequest;
import br.com.jaasielsilva.gestaojuridica.auth.AuthDtos.ResetPasswordRequest;
import br.com.jaasielsilva.gestaojuridica.auth.AuthDtos.TokenRequest;
import br.com.jaasielsilva.gestaojuridica.auth.AuthDtos.UserResponse;
import br.com.jaasielsilva.gestaojuridica.auth.AuthService.LoginOutcome;
import br.com.jaasielsilva.gestaojuridica.auth.mfa.MfaService;
import br.com.jaasielsilva.gestaojuridica.auth.session.SessionService.IssuedTokens;
import br.com.jaasielsilva.gestaojuridica.config.AppProperties;
import br.com.jaasielsilva.gestaojuridica.security.AuthPrincipal;
import br.com.jaasielsilva.gestaojuridica.shared.BusinessException;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private static final String XHR_HEADER = "X-Requested-With";

    private final AuthService auth;
    private final AccountService account;
    private final MfaService mfa;
    private final RefreshCookie cookie;
    private final AppProperties props;

    public AuthController(AuthService auth, AccountService account, MfaService mfa, RefreshCookie cookie,
                          AppProperties props) {
        this.auth = auth;
        this.account = account;
        this.mfa = mfa;
        this.cookie = cookie;
        this.props = props;
    }

    @GetMapping("/config")
    public AuthConfigResponse config() {
        return new AuthConfigResponse(props.registrationEnabled());
    }

    // ---------- cadastro e login ----------

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public MessageResponse register(@Valid @RequestBody RegisterRequest request) {
        auth.register(request);
        return new MessageResponse("Se o cadastro puder ser concluido, enviaremos instrucoes para o e-mail informado.");
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return respond(auth.login(request));
    }

    @PostMapping("/mfa/verify")
    public ResponseEntity<LoginResponse> verifyMfa(@Valid @RequestBody MfaVerifyRequest request) {
        return respond(auth.verifyMfa(request.mfaToken(), request.codigo()));
    }

    // ---------- sessao (usam o cookie de refresh, entao exigem X-Requested-With) ----------

    @PostMapping("/refresh")
    public ResponseEntity<LoginResponse> refresh(
            @CookieValue(name = RefreshCookie.NAME, required = false) String refreshToken,
            @RequestHeader(name = XHR_HEADER, required = false) String xhr) {
        requireXhr(xhr);
        try {
            return respond(new LoginOutcome(auth.refresh(refreshToken), null));
        } catch (BusinessException ex) {
            // Cookie invalido/expirado: manda o navegador apagar.
            return ResponseEntity.status(ex.getStatus()).header(HttpHeaders.SET_COOKIE, cookie.clear()).build();
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @CookieValue(name = RefreshCookie.NAME, required = false) String refreshToken,
            @RequestHeader(name = XHR_HEADER, required = false) String xhr,
            @AuthenticationPrincipal AuthPrincipal principal) {
        requireXhr(xhr);
        auth.logout(refreshToken, principal);
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, cookie.clear()).build();
    }

    @PostMapping("/logout-all")
    public ResponseEntity<Void> logoutAll(@AuthenticationPrincipal AuthPrincipal principal) {
        auth.logoutAll(principal);
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, cookie.clear()).build();
    }

    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal AuthPrincipal principal) {
        return auth.me(principal.userId());
    }

    // ---------- e-mail e senha ----------

    @PostMapping("/verify-email")
    public MessageResponse verifyEmail(@Valid @RequestBody TokenRequest request) {
        account.verifyEmail(request.token());
        return new MessageResponse("E-mail confirmado. Voce ja pode entrar.");
    }

    @PostMapping("/resend-verification")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public MessageResponse resendVerification(@Valid @RequestBody EmailRequest request) {
        account.resendVerification(request.email());
        return new MessageResponse("Se existir uma conta pendente para este e-mail, enviamos um novo link.");
    }

    @PostMapping("/forgot-password")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public MessageResponse forgotPassword(@Valid @RequestBody EmailRequest request) {
        account.forgotPassword(request.email());
        return new MessageResponse("Se existir uma conta para este e-mail, enviamos as instrucoes.");
    }

    @PostMapping("/reset-password")
    public MessageResponse resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        account.resetPassword(request.token(), request.novaSenha());
        return new MessageResponse("Senha redefinida. Entre com a nova senha.");
    }

    @PostMapping("/change-password")
    public MessageResponse changePassword(@AuthenticationPrincipal AuthPrincipal principal,
                                          @Valid @RequestBody ChangePasswordRequest request) {
        account.changePassword(principal, request.senhaAtual(), request.novaSenha());
        return new MessageResponse("Senha alterada. Os outros dispositivos foram desconectados.");
    }

    // ---------- MFA (usuario autenticado) ----------

    @PostMapping("/mfa/setup")
    public MfaSetupResponse mfaSetup(@AuthenticationPrincipal AuthPrincipal principal) {
        var setup = mfa.setup(principal.userId());
        return new MfaSetupResponse(setup.secret(), setup.otpauthUri());
    }

    @PostMapping("/mfa/enable")
    public RecoveryCodesResponse mfaEnable(@AuthenticationPrincipal AuthPrincipal principal,
                                           @Valid @RequestBody MfaEnableRequest request) {
        return new RecoveryCodesResponse(mfa.enable(principal.userId(), request.codigo()));
    }

    @PostMapping("/mfa/disable")
    public MessageResponse mfaDisable(@AuthenticationPrincipal AuthPrincipal principal,
                                      @Valid @RequestBody MfaDisableRequest request) {
        mfa.disable(principal.userId(), request.senha(), request.codigo());
        return new MessageResponse("MFA desativado.");
    }

    // ---------- helpers ----------

    private ResponseEntity<LoginResponse> respond(LoginOutcome outcome) {
        if (outcome.mfaRequired()) {
            return ResponseEntity.ok(new LoginResponse(null, null, null, true, outcome.mfaToken()));
        }
        IssuedTokens t = outcome.tokens();
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.issue(t.refreshToken(), t.refreshTtl()))
                .body(new LoginResponse(t.accessToken(), "Bearer", t.expiresInSeconds(), null, null));
    }

    /** Defesa em profundidade contra CSRF: navegadores nao enviam este header em requisicoes cross-site simples. */
    private static void requireXhr(String header) {
        if (header == null || header.isBlank()) {
            throw BusinessException.forbidden("Requisicao invalida");
        }
    }
}
