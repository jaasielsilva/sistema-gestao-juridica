package br.com.jaasielsilva.gestaojuridica.integracao.escavador;

import br.com.jaasielsilva.gestaojuridica.IntegrationTestBase;
import br.com.jaasielsilva.gestaojuridica.mail.AccountMailer;
import br.com.jaasielsilva.gestaojuridica.tenant.Tenant;
import br.com.jaasielsilva.gestaojuridica.tenant.TenantRepository;
import br.com.jaasielsilva.gestaojuridica.user.Papel;
import br.com.jaasielsilva.gestaojuridica.user.Usuario;
import br.com.jaasielsilva.gestaojuridica.user.UsuarioRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integracao do Escavador: credencial por tenant, isolamento entre dois tenants, cota em reais e
 * autenticacao do callback (ver ADR 0007). EscavadorClient e mockado — estes testes nao fazem
 * chamada de rede real ao Escavador.
 */
@SpringBootTest
@AutoConfigureMockMvc
class EscavadorIntegrationTest extends IntegrationTestBase {

    private static final String PASSWORD = "senha-forte-123";
    private static final String CNJ_A = "0000009-15.2015.8.26.0558";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UsuarioRepository usuarios;
    @Autowired TenantRepository tenants;
    @Autowired PasswordEncoder encoder;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean AccountMailer mailer;
    @MockitoBean EscavadorClient escavadorClient;

    private long tenantA;
    private long tenantB;
    private String adminA;
    private String userA;
    private String adminB;
    private long clienteA;
    private long processoA;

    @BeforeEach
    void cenario() throws Exception {
        tenantA = novoTenant("Escritorio A");
        tenantB = novoTenant("Escritorio B");
        adminA = loginDe(novoUsuario(tenantA, Papel.ADMIN));
        userA = loginDe(novoUsuario(tenantA, Papel.USER));
        adminB = loginDe(novoUsuario(tenantB, Papel.ADMIN));
        jdbc.update("DELETE FROM movimentacao WHERE tenant_id IN (?, ?)", tenantA, tenantB);
        jdbc.update("DELETE FROM processo WHERE tenant_id IN (?, ?)", tenantA, tenantB);
        jdbc.update("DELETE FROM cliente WHERE tenant_id IN (?, ?)", tenantA, tenantB);
        jdbc.update("DELETE FROM escavador_configuracao WHERE tenant_id IN (?, ?)", tenantA, tenantB);
        clienteA = criarCliente(adminA, "{\"tipo\":\"PF\",\"nome\":\"Joao da Silva\",\"documento\":\"529.982.247-25\"}");
        processoA = criarProcesso(adminA, clienteA);
    }

    private long novoTenant(String nome) {
        long id = tenants.nextId();
        tenants.save(new Tenant(id, nome + " " + id));
        return id;
    }

    private String novoUsuario(long tenant, Papel papel) {
        String email = "u" + UUID.randomUUID().toString().substring(0, 8) + "@teste.com";
        Usuario u = new Usuario(tenant, "Usuario " + papel, email, encoder.encode(PASSWORD), papel);
        u.marcarEmailVerificado();
        usuarios.save(u);
        return email;
    }

    private String loginDe(String email) throws Exception {
        MvcResult r = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", email, "senha", PASSWORD))))
                .andExpect(status().isOk()).andReturn();
        return json.readTree(r.getResponse().getContentAsString()).path("accessToken").asText();
    }

    private long criarCliente(String token, String body) throws Exception {
        MvcResult r = mvc.perform(post("/api/v1/clientes").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn();
        return json.readTree(r.getResponse().getContentAsString()).path("id").asLong();
    }

    private long criarProcesso(String token, long clienteId) throws Exception {
        String body = "{\"numero\":\"" + CNJ_A + "\",\"clienteId\":" + clienteId + ",\"tipo\":\"CIVIL\","
                + "\"areaJuridica\":\"Direito Civil\",\"tribunal\":\"TJSP - Comarca de Sao Paulo\","
                + "\"advogadoNome\":\"Jasiel Miranda\",\"situacao\":\"EM_ANDAMENTO\"}";
        MvcResult r = mvc.perform(post("/api/v1/processos").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn();
        return json.readTree(r.getResponse().getContentAsString()).path("id").asLong();
    }

    private void configurar(String token, long saldoLimiteCentavos) throws Exception {
        mvc.perform(put("/api/v1/integracoes/escavador/configuracao").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "token", "pat-de-teste",
                                "callbackSecret", "segredo-de-teste",
                                "saldoLimiteCentavos", saldoLimiteCentavos))))
                .andExpect(status().isOk());
    }

    @Test
    void apenasAdminConfiguraAChave() throws Exception {
        mvc.perform(put("/api/v1/integracoes/escavador/configuracao").header("Authorization", "Bearer " + userA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("token", "x", "callbackSecret", "y", "saldoLimiteCentavos", 1000))))
                .andExpect(status().isForbidden());
    }

    @Test
    void configuracaoNuncaDevolveOTokenOuOSegredo() throws Exception {
        configurar(adminA, 1000);

        mvc.perform(get("/api/v1/integracoes/escavador/configuracao").header("Authorization", "Bearer " + adminA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configurado").value(true))
                .andExpect(jsonPath("$.urlCallback").exists())
                .andExpect(content -> {
                    String body = content.getResponse().getContentAsString();
                    assertThat(body).doesNotContain("pat-de-teste").doesNotContain("segredo-de-teste");
                });
    }

    @Test
    void ativaMonitoramentoChamaOEscavadorESalvaOIdRetornado() throws Exception {
        configurar(adminA, 1000);
        when(escavadorClient.criarMonitoramento(anyString(), anyString(), any()))
                .thenReturn(new EscavadorClient.MonitoramentoResposta(4544414L, new EscavadorClient.MonitoramentoResposta.Monitor("FOUND")));

        mvc.perform(post("/api/v1/integracoes/escavador/processos/" + processoA + "/monitoramento")
                        .header("Authorization", "Bearer " + adminA).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"frequencia\":\"MENSAL\"}"))
                .andExpect(status().isNoContent());

        assertThat(jdbc.queryForObject("SELECT monitoramento_escavador_id FROM processo WHERE id = ?", Long.class, processoA))
                .isEqualTo(4544414L);
    }

    @Test
    void bloqueiaAtivacaoQueUltrapassaOLimiteMensalConfigurado() throws Exception {
        configurar(adminA, 10); // 10 centavos: nem a frequencia mensal (8) + qualquer outra caberia depois

        mvc.perform(post("/api/v1/integracoes/escavador/processos/" + processoA + "/monitoramento")
                        .header("Authorization", "Bearer " + adminA).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"frequencia\":\"DIARIA\"}"))
                .andExpect(status().isConflict());
        Mockito.verifyNoInteractions(escavadorClient);
    }

    @Test
    void tenantBNaoVeConfiguracaoNemAtivaMonitoramentoNoProcessoDoTenantA() throws Exception {
        configurar(adminA, 1000);

        mvc.perform(get("/api/v1/integracoes/escavador/configuracao").header("Authorization", "Bearer " + adminB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configurado").value(false));

        mvc.perform(post("/api/v1/integracoes/escavador/processos/" + processoA + "/monitoramento")
                        .header("Authorization", "Bearer " + adminB).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"frequencia\":\"MENSAL\"}"))
                .andExpect(status().isNotFound()); // processo de outro tenant: nao encontrado, nao "acesso negado"
    }

    @Test
    void callbackComSegredoErradoENaoAutenticado() throws Exception {
        configurar(adminA, 1000);
        UUID slug = UUID.fromString(jdbc.queryForObject(
                "SELECT CAST(callback_slug AS VARCHAR(36)) FROM escavador_configuracao WHERE tenant_id = ?", String.class, tenantA));

        mvc.perform(post("/api/v1/integracoes/escavador/callback/" + slug)
                        .header("Authorization", "Bearer segredo-errado")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"numero_cnj\":\"" + CNJ_A + "\",\"evento\":\"movimentacao_nova\",\"descricao\":\"teste\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void callbackComSlugInexistenteDevolve404SemRevelarNada() throws Exception {
        mvc.perform(post("/api/v1/integracoes/escavador/callback/" + UUID.randomUUID())
                        .header("Authorization", "Bearer qualquer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void callbackValidoGravaMovimentacaoEEIdempotente() throws Exception {
        configurar(adminA, 1000);
        UUID slug = UUID.fromString(jdbc.queryForObject(
                "SELECT CAST(callback_slug AS VARCHAR(36)) FROM escavador_configuracao WHERE tenant_id = ?", String.class, tenantA));
        String payload = "{\"numero_cnj\":\"" + CNJ_A + "\",\"evento\":\"movimentacao_nova\",\"descricao\":\"Juntada de peticao\"}";

        mvc.perform(post("/api/v1/integracoes/escavador/callback/" + slug)
                        .header("Authorization", "Bearer segredo-de-teste")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk());
        // reentrega do mesmo evento (o Escavador documenta isso como esperado) — nao deve duplicar
        mvc.perform(post("/api/v1/integracoes/escavador/callback/" + slug)
                        .header("Authorization", "Bearer segredo-de-teste")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk());

        Integer total = jdbc.queryForObject("SELECT COUNT(*) FROM movimentacao WHERE processo_id = ?", Integer.class, processoA);
        assertThat(total).isEqualTo(1);

        mvc.perform(get("/api/v1/integracoes/escavador/processos/" + processoA + "/movimentacoes")
                        .header("Authorization", "Bearer " + userA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].descricao").value("Juntada de peticao"));
    }
}
