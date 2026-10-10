package com.example.app.auth;

import com.example.app.IntegrationTestBase;
import com.example.app.auth.mfa.SecretCipher;
import com.example.app.auth.mfa.TotpService;
import com.example.app.auth.throttle.ThrottleService;
import com.example.app.mail.AccountMailer;
import com.example.app.shared.TooManyRequestsException;
import com.example.app.tenant.Tenant;
import com.example.app.tenant.TenantRepository;
import com.example.app.user.Papel;
import com.example.app.user.Usuario;
import com.example.app.user.UsuarioRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Integracao ponta a ponta contra SQL Server real: autenticacao, sessoes, MFA, bloqueio, auditoria e isolamento de tenant. */
@SpringBootTest
@AutoConfigureMockMvc
class AuthIntegrationTest extends IntegrationTestBase {

    private static final String PASSWORD = "senha-forte-123";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UsuarioRepository usuarios;
    @Autowired TenantRepository tenants;
    @Autowired PasswordEncoder encoder;
    @Autowired JdbcTemplate jdbc;
    @Autowired SecretCipher cipher;
    @Autowired TotpService totp;
    @Autowired ThrottleService throttle;
    @MockitoBean AccountMailer mailer;

    // ---------- helpers ----------

    record Tokens(String access, String refresh) {
    }

    private static String email() {
        return "u" + UUID.randomUUID().toString().substring(0, 8) + "@teste.com";
    }

    private ResultActions postJson(String path, String body) throws Exception {
        return mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private static String body(String... kv) {
        StringBuilder sb = new StringBuilder("{");
        for (int i = 0; i < kv.length; i += 2) {
            sb.append(i > 0 ? "," : "").append('"').append(kv[i]).append("\":\"").append(kv[i + 1]).append('"');
        }
        return sb.append('}').toString();
    }

    /** Cadastra e devolve o token de verificacao que seria enviado por e-mail. */
    private String register(String email) throws Exception {
        postJson("/api/v1/auth/register", body("nome", "Teste", "email", email, "senha", PASSWORD))
                .andExpect(status().isCreated());
        ArgumentCaptor<String> token = ArgumentCaptor.forClass(String.class);
        verify(mailer).sendVerification(eq(email), any(), token.capture());
        return token.getValue();
    }

    private void verifyEmail(String token) throws Exception {
        postJson("/api/v1/auth/verify-email", body("token", token)).andExpect(status().isOk());
    }

    private String registerAndVerify(String email) throws Exception {
        verifyEmail(register(email));
        return email;
    }

    private Tokens tokensFrom(MvcResult result) throws Exception {
        JsonNode node = json.readTree(result.getResponse().getContentAsString());
        Cookie cookie = result.getResponse().getCookie("refresh_token");
        return new Tokens(node.path("accessToken").asText(null), cookie == null ? null : cookie.getValue());
    }

    private Tokens login(String email, String password) throws Exception {
        MvcResult r = postJson("/api/v1/auth/login", body("email", email, "senha", password))
                .andExpect(status().isOk()).andReturn();
        return tokensFrom(r);
    }

    private ResultActions me(String access) throws Exception {
        return mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + access));
    }

    private ResultActions refresh(String refreshToken) throws Exception {
        return mvc.perform(post("/api/v1/auth/refresh").header("X-Requested-With", "XMLHttpRequest")
                .cookie(new Cookie("refresh_token", refreshToken)));
    }

    // ---------- cadastro, verificacao de e-mail e login ----------

    @Test
    void cadastroExigeVerificacaoDeEmailAntesDoLogin() throws Exception {
        String email = email();
        String token = register(email);

        postJson("/api/v1/auth/login", body("email", email, "senha", PASSWORD))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Confirme seu e-mail antes de entrar"));

        verifyEmail(token);
        Tokens t = login(email, PASSWORD);

        me(t.access()).andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.papel").value("USER"))
                .andExpect(jsonPath("$.emailVerificado").value(true))
                .andExpect(jsonPath("$.mfaAtivo").value(false))
                .andExpect(jsonPath("$.tenantId").value(1))
                .andExpect(jsonPath("$.senhaHash").doesNotExist());
        // o mesmo link nao vale duas vezes
        postJson("/api/v1/auth/verify-email", body("token", token)).andExpect(status().isBadRequest());
    }

    @Test
    void loginDevolveRefreshEmCookieHttpOnlySameSiteStrictESemVazarNoCorpo() throws Exception {
        String email = registerAndVerify(email());

        MvcResult r = postJson("/api/v1/auth/login", body("email", email, "senha", PASSWORD)).andReturn();

        String setCookie = r.getResponse().getHeader("Set-Cookie");
        assertThat(setCookie).contains("refresh_token=", "HttpOnly", "SameSite=Strict", "Path=/api/v1/auth");
        assertThat(r.getResponse().getContentAsString()).doesNotContain("refresh");
    }

    @Test
    void cadastroDuplicadoRetorna409ESenhaFracaOuEmailInvalidoRetorna400() throws Exception {
        String email = registerAndVerify(email());
        postJson("/api/v1/auth/register", body("nome", "X", "email", email, "senha", PASSWORD))
                .andExpect(status().isConflict());
        postJson("/api/v1/auth/register", body("nome", "X", "email", "nao-e-email", "senha", "curta"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.violations").isNotEmpty());
    }

    @Test
    void loginInvalidoRetorna401SemVazarDetalhes() throws Exception {
        postJson("/api/v1/auth/login", body("email", email(), "senha", "qualquer"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Credenciais invalidas"));
    }

    @Test
    void rotaProtegidaSemTokenOuComTokenInvalidoRetorna401() throws Exception {
        mvc.perform(get("/api/v1/auth/me")).andExpect(status().isUnauthorized());
        me("lixo").andExpect(status().isUnauthorized());
    }

    @Test
    void respostasTemCabecalhosDeSegurancaEJsonInvalidoNaoVazaDetalhes() throws Exception {
        mvc.perform(get("/api/v1/auth/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.registrationEnabled").value(true))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(header().string("Content-Security-Policy", containsString("frame-ancestors 'none'")))
                .andExpect(header().exists("X-Correlation-Id"));
        postJson("/api/v1/auth/login", "{quebrado")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(not(containsString("Exception"))));
    }

    // ---------- limite de tentativas ----------

    @Test
    void cincoSenhasErradasBloqueiamAContaMesmoComASenhaCorreta() throws Exception {
        String email = registerAndVerify(email());
        for (int i = 0; i < 5; i++) {
            postJson("/api/v1/auth/login", body("email", email, "senha", "errada-" + i)).andExpect(status().isUnauthorized());
        }

        postJson("/api/v1/auth/login", body("email", email, "senha", PASSWORD))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));
    }

    @Test
    void emailInexistenteTambemEBloqueadoParaNaoRevelarQuaisContasExistem() throws Exception {
        String ghost = email();
        for (int i = 0; i < 5; i++) {
            postJson("/api/v1/auth/login", body("email", ghost, "senha", "x" + i)).andExpect(status().isUnauthorized());
        }
        postJson("/api/v1/auth/login", body("email", ghost, "senha", "x")).andExpect(status().isTooManyRequests());
    }

    @Test
    void throttleServiceBloqueiaNoLimiteELiberaNoReset() {
        String key = "teste:" + UUID.randomUUID();
        throttle.hit(key, 3);
        throttle.hit(key, 3);
        throttle.assertNotBlocked(key);
        throttle.hit(key, 3);

        assertThatThrownBy(() -> throttle.assertNotBlocked(key)).isInstanceOf(TooManyRequestsException.class);
        throttle.reset(key);
        throttle.assertNotBlocked(key);
    }

    // ---------- sessao, refresh e logout ----------

    @Test
    void refreshRotacionaOTokenEReusoDoAntigoRevogaASessaoInteira() throws Exception {
        String email = registerAndVerify(email());
        Tokens first = login(email, PASSWORD);

        MvcResult renewed = refresh(first.refresh()).andExpect(status().isOk()).andReturn();
        Tokens second = tokensFrom(renewed);
        assertThat(second.refresh()).isNotEqualTo(first.refresh());
        me(second.access()).andExpect(status().isOk());

        // Reapresentar o refresh token antigo = possivel roubo: derruba a sessao inteira.
        refresh(first.refresh()).andExpect(status().isUnauthorized())
                .andExpect(header().string("Set-Cookie", containsString("Max-Age=0")));
        refresh(second.refresh()).andExpect(status().isUnauthorized());
        me(second.access()).andExpect(status().isUnauthorized());
    }

    @Test
    void refreshSemCabecalhoXRequestedWithOuSemCookieENegado() throws Exception {
        String email = registerAndVerify(email());
        Tokens t = login(email, PASSWORD);

        mvc.perform(post("/api/v1/auth/refresh").cookie(new Cookie("refresh_token", t.refresh())))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/auth/refresh").header("X-Requested-With", "XMLHttpRequest"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logoutInvalidaOAccessTokenNaHoraENaoDeixaRenovar() throws Exception {
        String email = registerAndVerify(email());
        Tokens t = login(email, PASSWORD);
        me(t.access()).andExpect(status().isOk());

        mvc.perform(post("/api/v1/auth/logout").header("X-Requested-With", "XMLHttpRequest")
                        .header("Authorization", "Bearer " + t.access())
                        .cookie(new Cookie("refresh_token", t.refresh())))
                .andExpect(status().isNoContent());

        me(t.access()).andExpect(status().isUnauthorized());
        refresh(t.refresh()).andExpect(status().isUnauthorized());
    }

    @Test
    void logoutAllDerrubaTodosOsDispositivos() throws Exception {
        String email = registerAndVerify(email());
        Tokens a = login(email, PASSWORD);
        Tokens b = login(email, PASSWORD);

        mvc.perform(post("/api/v1/auth/logout-all").header("Authorization", "Bearer " + a.access()))
                .andExpect(status().isNoContent());

        me(a.access()).andExpect(status().isUnauthorized());
        me(b.access()).andExpect(status().isUnauthorized());
    }

    // ---------- recuperacao e troca de senha ----------

    @Test
    void esqueciASenhaRedefineDerrubaSessoesEOLinkValeUmaVez() throws Exception {
        String email = registerAndVerify(email());
        Tokens old = login(email, PASSWORD);

        postJson("/api/v1/auth/forgot-password", body("email", email)).andExpect(status().isAccepted());
        ArgumentCaptor<String> token = ArgumentCaptor.forClass(String.class);
        verify(mailer).sendPasswordReset(eq(email), any(), token.capture());

        postJson("/api/v1/auth/reset-password", body("token", token.getValue(), "novaSenha", "nova-senha-456"))
                .andExpect(status().isOk());

        me(old.access()).andExpect(status().isUnauthorized()); // sessoes antigas caem
        postJson("/api/v1/auth/login", body("email", email, "senha", PASSWORD)).andExpect(status().isUnauthorized());
        login(email, "nova-senha-456");
        postJson("/api/v1/auth/reset-password", body("token", token.getValue(), "novaSenha", "outra-senha-789"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void esqueciASenhaDeEmailInexistenteResponde202IgualENaoEnviaEmail() throws Exception {
        String ghost = email();
        postJson("/api/v1/auth/forgot-password", body("email", ghost))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.message").value("Se existir uma conta para este e-mail, enviamos as instrucoes."));
        verify(mailer, never()).sendPasswordReset(eq(ghost), any(), any());
    }

    @Test
    void trocarSenhaExigeSenhaAtualEMantemSoASessaoAtual() throws Exception {
        String email = registerAndVerify(email());
        Tokens current = login(email, PASSWORD);
        Tokens other = login(email, PASSWORD);

        mvc.perform(post("/api/v1/auth/change-password").header("Authorization", "Bearer " + current.access())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("senhaAtual", "errada-errada", "novaSenha", "nova-senha-456")))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/auth/change-password").header("Authorization", "Bearer " + current.access())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("senhaAtual", PASSWORD, "novaSenha", "nova-senha-456")))
                .andExpect(status().isOk());

        me(current.access()).andExpect(status().isOk());
        me(other.access()).andExpect(status().isUnauthorized());
        login(email, "nova-senha-456");
    }

    // ---------- MFA ----------

    private byte[] secretOf(String email) {
        Usuario u = usuarios.findByEmail(email).orElseThrow();
        return cipher.decrypt(u.getMfaSegredo());
    }

    private String enableMfa(String email, Tokens t) throws Exception {
        mvc.perform(post("/api/v1/auth/mfa/setup").header("Authorization", "Bearer " + t.access()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.secret").isNotEmpty())
                .andExpect(jsonPath("$.otpauthUri").value(containsString("otpauth://totp/")));
        // codigo errado nao ativa
        mvc.perform(post("/api/v1/auth/mfa/enable").header("Authorization", "Bearer " + t.access())
                        .contentType(MediaType.APPLICATION_JSON).content(body("codigo", "000000")))
                .andExpect(status().isBadRequest());
        String code = totp.codeFor(secretOf(email), totp.stepAt(Instant.now()));
        MvcResult r = mvc.perform(post("/api/v1/auth/mfa/enable").header("Authorization", "Bearer " + t.access())
                        .contentType(MediaType.APPLICATION_JSON).content(body("codigo", code)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.codigos.length()").value(8))
                .andReturn();
        return json.readTree(r.getResponse().getContentAsString()).path("codigos").get(0).asText();
    }

    @Test
    void mfaLoginExigeSegundoFatorAceitaTotpEBloqueiaReplay() throws Exception {
        String email = registerAndVerify(email());
        enableMfa(email, login(email, PASSWORD));
        assertThat(usuarios.findByEmail(email).orElseThrow().getMfaSegredo()).doesNotContain(totp.base32(secretOf(email)));

        MvcResult step1 = postJson("/api/v1/auth/login", body("email", email, "senha", PASSWORD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mfaRequired").value(true))
                .andExpect(jsonPath("$.accessToken").doesNotExist())
                .andReturn();
        assertThat(step1.getResponse().getHeader("Set-Cookie")).isNull();
        String mfaToken = json.readTree(step1.getResponse().getContentAsString()).path("mfaToken").asText();

        postJson("/api/v1/auth/mfa/verify", body("mfaToken", mfaToken, "codigo", "000000")).andExpect(status().isUnauthorized());
        // o passo do codigo usado na ativacao nao serve de novo (replay); o do proximo passo serve
        String replay = totp.codeFor(secretOf(email), totp.stepAt(Instant.now()));
        postJson("/api/v1/auth/mfa/verify", body("mfaToken", mfaToken, "codigo", replay)).andExpect(status().isUnauthorized());
        String next = totp.codeFor(secretOf(email), totp.stepAt(Instant.now()) + 1);
        MvcResult ok = postJson("/api/v1/auth/mfa/verify", body("mfaToken", mfaToken, "codigo", next))
                .andExpect(status().isOk()).andReturn();
        me(tokensFrom(ok).access()).andExpect(status().isOk()).andExpect(jsonPath("$.mfaAtivo").value(true));
    }

    @Test
    void tokenMfaNaoAbreRotasProtegidas() throws Exception {
        String email = registerAndVerify(email());
        enableMfa(email, login(email, PASSWORD));
        String mfaToken = json.readTree(postJson("/api/v1/auth/login", body("email", email, "senha", PASSWORD))
                .andReturn().getResponse().getContentAsString()).path("mfaToken").asText();

        me(mfaToken).andExpect(status().isUnauthorized());
    }

    @Test
    void codigoDeRecuperacaoFuncionaUmaUnicaVez() throws Exception {
        String email = registerAndVerify(email());
        String recovery = enableMfa(email, login(email, PASSWORD));

        for (int attempt = 0; attempt < 2; attempt++) {
            String mfaToken = json.readTree(postJson("/api/v1/auth/login", body("email", email, "senha", PASSWORD))
                    .andReturn().getResponse().getContentAsString()).path("mfaToken").asText();
            postJson("/api/v1/auth/mfa/verify", body("mfaToken", mfaToken, "codigo", recovery))
                    .andExpect(attempt == 0 ? status().isOk() : status().isUnauthorized());
        }
    }

    @Test
    void desativarMfaExigeSenhaECodigo() throws Exception {
        String email = registerAndVerify(email());
        Tokens t = login(email, PASSWORD);
        String recovery = enableMfa(email, t);

        mvc.perform(post("/api/v1/auth/mfa/disable").header("Authorization", "Bearer " + t.access())
                        .contentType(MediaType.APPLICATION_JSON).content(body("senha", "errada-errada", "codigo", recovery)))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/mfa/disable").header("Authorization", "Bearer " + t.access())
                        .contentType(MediaType.APPLICATION_JSON).content(body("senha", PASSWORD, "codigo", recovery)))
                .andExpect(status().isOk());

        Usuario u = usuarios.findByEmail(email).orElseThrow();
        assertThat(u.isMfaAtivo()).isFalse();
        assertThat(u.getMfaSegredo()).isNull();
        login(email, PASSWORD); // volta a logar so com a senha
    }

    // ---------- autorizacao, auditoria e tenant ----------

    @Test
    void areaAdminEAuditoriaSoParaAdminESoDoProprioTenant() throws Exception {
        String user = registerAndVerify(email());
        String admin = registerAndVerify(email());
        jdbc.update("UPDATE usuario SET papel = 'ADMIN' WHERE email = ?", admin);
        Tokens userTokens = login(user, PASSWORD);
        Tokens adminTokens = login(admin, PASSWORD);

        mvc.perform(get("/api/v1/admin/auditoria").header("Authorization", "Bearer " + userTokens.access()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Acesso negado"));
        mvc.perform(get("/api/v1/admin/auditoria").header("Authorization", "Bearer " + adminTokens.access()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itens[?(@.tipo=='LOGIN_SUCESSO')]").isNotEmpty())
                .andExpect(jsonPath("$.itens[?(@.tipo=='CADASTRO')]").isNotEmpty());
    }

    @Test
    void auditoriaRegistraFalhasDeLoginComEmailEIpMasSemSenha() throws Exception {
        String email = registerAndVerify(email());
        postJson("/api/v1/auth/login", body("email", email, "senha", "senha-errada-xyz")).andExpect(status().isUnauthorized());

        Integer falhas = jdbc.queryForObject(
                "SELECT COUNT(*) FROM evento_auditoria WHERE tipo = 'LOGIN_FALHA' AND resultado = 'FALHA' AND email_informado = ?",
                Integer.class, email);
        Integer vazouSenha = jdbc.queryForObject(
                "SELECT COUNT(*) FROM evento_auditoria WHERE detalhe LIKE '%senha-errada-xyz%'", Integer.class);

        assertThat(falhas).isEqualTo(1);
        assertThat(vazouSenha).isZero();
    }

    @Test
    void tenantNaoEnxergaDadosDeOutroTenantPorListagemIdOuExclusao() throws Exception {
        tenants.save(new Tenant(2L, "Empresa B"));
        String emailA = registerAndVerify(email());
        String emailB = email();
        Usuario b = new Usuario(2L, "Usuario B", emailB, encoder.encode(PASSWORD), Papel.USER);
        b.marcarEmailVerificado();
        usuarios.save(b);
        Tokens a = login(emailA, PASSWORD);
        Tokens tb = login(emailB, PASSWORD);

        MvcResult created = mvc.perform(post("/api/v1/notas").header("Authorization", "Bearer " + a.access())
                        .contentType(MediaType.APPLICATION_JSON).content(body("titulo", "Segredo da empresa A")))
                .andExpect(status().isCreated()).andReturn();
        long notaId = json.readTree(created.getResponse().getContentAsString()).path("id").asLong();
        assertThat(jdbc.queryForObject("SELECT tenant_id FROM nota WHERE id = ?", Long.class, notaId)).isEqualTo(1L);

        // Tenant B, usando o ID conhecido da nota do tenant A:
        mvc.perform(get("/api/v1/notas/" + notaId).header("Authorization", "Bearer " + tb.access()))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/v1/notas/" + notaId).header("Authorization", "Bearer " + tb.access()))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/notas").header("Authorization", "Bearer " + tb.access()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        // A nota continua intacta e visivel para o dono.
        mvc.perform(get("/api/v1/notas/" + notaId).header("Authorization", "Bearer " + a.access()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.titulo").value("Segredo da empresa A"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM nota WHERE id = ?", Integer.class, notaId)).isEqualTo(1);
    }

    @Test
    void notasSemLoginRetorna401() throws Exception {
        mvc.perform(get("/api/v1/notas")).andExpect(status().isUnauthorized());
    }
}
