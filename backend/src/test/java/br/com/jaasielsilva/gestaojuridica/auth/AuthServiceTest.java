package br.com.jaasielsilva.gestaojuridica.auth;

import br.com.jaasielsilva.gestaojuridica.TestProps;
import br.com.jaasielsilva.gestaojuridica.audit.AuditService;
import br.com.jaasielsilva.gestaojuridica.audit.TipoEvento;
import br.com.jaasielsilva.gestaojuridica.auth.AuthDtos.LoginRequest;
import br.com.jaasielsilva.gestaojuridica.auth.AuthDtos.RegisterRequest;
import br.com.jaasielsilva.gestaojuridica.auth.mfa.MfaService;
import br.com.jaasielsilva.gestaojuridica.auth.session.SessionService;
import br.com.jaasielsilva.gestaojuridica.auth.throttle.ThrottleService;
import br.com.jaasielsilva.gestaojuridica.auth.token.TipoToken;
import br.com.jaasielsilva.gestaojuridica.auth.token.TokenAcaoService;
import br.com.jaasielsilva.gestaojuridica.config.AppProperties;
import br.com.jaasielsilva.gestaojuridica.mail.AccountMailer;
import br.com.jaasielsilva.gestaojuridica.security.JwtService;
import br.com.jaasielsilva.gestaojuridica.shared.BusinessException;
import br.com.jaasielsilva.gestaojuridica.shared.RequestInfo;
import br.com.jaasielsilva.gestaojuridica.tenant.TenantContext;
import br.com.jaasielsilva.gestaojuridica.user.Papel;
import br.com.jaasielsilva.gestaojuridica.user.Usuario;
import br.com.jaasielsilva.gestaojuridica.user.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthServiceTest {

    private UsuarioRepository repo;
    private BCryptPasswordEncoder encoder;
    private SessionService sessions;
    private ThrottleService throttle;
    private AuditService audit;
    private MfaService mfa;
    private TokenAcaoService tokens;
    private AccountMailer mailer;
    private JwtService jwt;
    private RegistrationWriter registrationWriter;

    private AuthService service(boolean registrationEnabled) {
        AppProperties props = TestProps.app(registrationEnabled);
        RequestInfo request = mock(RequestInfo.class);
        when(request.ip()).thenReturn("10.0.0.1");
        return new AuthService(repo, encoder, jwt, sessions, throttle, audit, request, mfa, tokens, mailer, props,
                registrationWriter);
    }

    @BeforeEach
    void setUp() {
        repo = mock(UsuarioRepository.class);
        encoder = new BCryptPasswordEncoder(4); // custo baixo so para teste rapido
        sessions = mock(SessionService.class);
        throttle = mock(ThrottleService.class);
        when(throttle.maxLoginPerEmail()).thenReturn(5);
        when(throttle.maxLoginPerIp()).thenReturn(30);
        when(throttle.maxSensitivePerIp()).thenReturn(10);
        audit = mock(AuditService.class);
        mfa = mock(MfaService.class);
        tokens = mock(TokenAcaoService.class);
        mailer = mock(AccountMailer.class);
        jwt = new JwtService(TestProps.app(true));
        registrationWriter = mock(RegistrationWriter.class);
    }

    private Usuario user(boolean verified, boolean mfaOn) {
        Usuario u = org.mockito.Mockito.spy(new Usuario(1L, "A", "a@b.com", encoder.encode("senha-forte-123"), Papel.USER));
        org.mockito.Mockito.doReturn(10L).when(u).getId();
        org.mockito.Mockito.doReturn(verified).when(u).isEmailVerificado();
        org.mockito.Mockito.doReturn(mfaOn).when(u).isMfaAtivo();
        return u;
    }

    @Test
    void registerDesabilitadoRetorna403() {
        assertThatThrownBy(() -> service(false).register(new RegisterRequest("A", "Org", "a@b.com", "senha-forte-123")))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
        verify(repo, never()).saveAndFlush(any());
    }

    @Test
    void registerNormalizaEmailEEnviaVerificacaoParaTenantProvisionado() {
        when(repo.existsByEmail("joao@x.com")).thenReturn(false);
        when(registrationWriter.create(any(), eq("joao@x.com"), anyString()))
                .thenAnswer(i -> new Usuario(42L, "Joao", "joao@x.com", i.getArgument(2), Papel.ADMIN));
        when(tokens.emitir(any(), eq(TipoToken.VERIFICACAO_EMAIL), any(Duration.class))).thenReturn("tok-123");

        service(true).register(new RegisterRequest("  Joao ", "  Org ", "  Joao@X.com ", "senha-forte-123"));

        ArgumentCaptor<RegisterRequest> request = ArgumentCaptor.forClass(RegisterRequest.class);
        ArgumentCaptor<String> hash = ArgumentCaptor.forClass(String.class);
        verify(registrationWriter).create(request.capture(), eq("joao@x.com"), hash.capture());
        assertThat(request.getValue().nome()).isEqualTo("  Joao ");
        assertThat(request.getValue().organizacao()).isEqualTo("  Org ");
        assertThat(encoder.matches("senha-forte-123", hash.getValue())).isTrue();
        verify(mailer).sendVerification(eq("joao@x.com"), eq("Joao"), eq("tok-123"));
    }

    @Test
    void registerComEmailExistenteRetornaSemRevelarSeJaExiste() {
        when(repo.existsByEmail("a@b.com")).thenReturn(true);

        service(true).register(new RegisterRequest("A", "Org", "a@b.com", "senha-forte-123"));
        verify(registrationWriter, never()).create(any(), anyString(), anyString());
    }

    @Test
    void loginComSenhaErradaOuEmailInexistenteDevolveMesmoErroERegistraFalha() {
        Usuario existing = user(true, false);
        when(repo.findByEmail("a@b.com")).thenReturn(Optional.of(existing));
        when(repo.findByEmail("naoexiste@b.com")).thenReturn(Optional.empty());

        for (LoginRequest req : new LoginRequest[]{
                new LoginRequest("a@b.com", "errada"), new LoginRequest("naoexiste@b.com", "qualquer")}) {
            assertThatThrownBy(() -> service(true).login(req))
                    .isInstanceOfSatisfying(BusinessException.class, e -> {
                        assertThat(e.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
                        assertThat(e.getMessage()).isEqualTo("Credenciais invalidas");
                    });
        }
        // 2 falhas x (chave de e-mail + chave de IP)
        verify(throttle, org.mockito.Mockito.times(4)).hit(anyString(), anyInt());
        verify(audit, org.mockito.Mockito.times(2)).falha(eq(TipoEvento.LOGIN_FALHA), any(), any(), anyString(), anyString());
        verify(sessions, never()).start(any());
    }

    @Test
    void loginComEmailNaoVerificadoRetorna403SemCriarSessao() {
        Usuario unverified = user(false, false);
        when(repo.findByEmail("a@b.com")).thenReturn(Optional.of(unverified));

        assertThatThrownBy(() -> service(true).login(new LoginRequest("a@b.com", "senha-forte-123")))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
        verify(sessions, never()).start(any());
    }

    @Test
    void loginComMfaAtivoExigeSegundoFatorSemCriarSessao() {
        Usuario withMfa = user(true, true);
        when(repo.findByEmail("a@b.com")).thenReturn(Optional.of(withMfa));

        var outcome = service(true).login(new LoginRequest("a@b.com", "senha-forte-123"));

        assertThat(outcome.mfaRequired()).isTrue();
        assertThat(jwt.parseMfaToken(outcome.mfaToken())).contains(10L);
        verify(sessions, never()).start(any());
    }

    @Test
    void loginCorretoCriaSessaoELimpaOContadorDeFalhas() {
        Usuario u = user(true, false);
        when(repo.findByEmail("a@b.com")).thenReturn(Optional.of(u));
        var issued = new SessionService.IssuedTokens("acc", 300, "ref", Duration.ofDays(7));
        when(sessions.start(u)).thenReturn(issued);

        var outcome = service(true).login(new LoginRequest("A@b.com", "senha-forte-123"));

        assertThat(outcome.mfaRequired()).isFalse();
        assertThat(outcome.tokens()).isEqualTo(issued);
        verify(throttle).reset(ThrottleService.emailKey("login", "a@b.com"));
        verify(audit).sucesso(eq(TipoEvento.LOGIN_SUCESSO), eq(10L), eq(1L), any());
    }
}
