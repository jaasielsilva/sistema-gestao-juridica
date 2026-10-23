package br.com.jaasielsilva.gestaojuridica.cliente;

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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Cadastro de clientes contra SQL Server real: regras, permissoes e isolamento entre dois tenants. */
@SpringBootTest
@AutoConfigureMockMvc
class ClienteIntegrationTest extends IntegrationTestBase {

    private static final String PASSWORD = "senha-forte-123";
    private static final String CPF_A = "529.982.247-25";
    private static final String CNPJ_A = "11.222.333/0001-81";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UsuarioRepository usuarios;
    @Autowired TenantRepository tenants;
    @Autowired PasswordEncoder encoder;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean AccountMailer mailer;

    private long tenantA;
    private long tenantB;
    private String adminA;
    private String userA;
    private String adminB;

    @BeforeEach
    void cenario() throws Exception {
        tenantA = novoTenant("Escritorio A");
        tenantB = novoTenant("Escritorio B");
        adminA = loginDe(novoUsuario(tenantA, Papel.ADMIN));
        userA = loginDe(novoUsuario(tenantA, Papel.USER));
        adminB = loginDe(novoUsuario(tenantB, Papel.ADMIN));
        jdbc.update("DELETE FROM cliente WHERE tenant_id IN (?, ?)", tenantA, tenantB);
    }

    // ---------- helpers ----------

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

    private static String pf(String nome, String cpf) {
        return "{\"tipo\":\"PF\",\"nome\":\"" + nome + "\",\"documento\":\"" + cpf + "\"}";
    }

    private ResultActions criar(String token, String body) throws Exception {
        return mvc.perform(post("/api/v1/clientes").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private long criarOk(String token, String body) throws Exception {
        MvcResult r = criar(token, body).andExpect(status().isCreated()).andReturn();
        return json.readTree(r.getResponse().getContentAsString()).path("id").asLong();
    }

    private ResultActions consultar(String token, String path) throws Exception {
        return mvc.perform(get(path).header("Authorization", "Bearer " + token));
    }

    // ---------- caminho feliz ----------

    @Test
    void criaPessoaFisicaNormalizandoDocumentoEContatoNoTenantDoToken() throws Exception {
        MvcResult r = criar(userA, """
                {"tipo":"PF","nome":"  Joao da Silva ","documento":"529.982.247-25","email":"joao@exemplo.com",
                 "telefone":"(11) 98765-4321","cep":"01310-100","uf":"sp","cidade":"Sao Paulo"}""")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nome").value("Joao da Silva"))
                .andExpect(jsonPath("$.documento").value("52998224725"))
                .andExpect(jsonPath("$.telefone").value("11987654321"))
                .andExpect(jsonPath("$.cep").value("01310100"))
                .andExpect(jsonPath("$.uf").value("SP"))
                .andExpect(jsonPath("$.ativo").value(true))
                .andReturn();
        long id = json.readTree(r.getResponse().getContentAsString()).path("id").asLong();

        assertThat(jdbc.queryForObject("SELECT tenant_id FROM cliente WHERE id = ?", Long.class, id)).isEqualTo(tenantA);
        consultar(userA, "/api/v1/clientes/" + id).andExpect(status().isOk()).andExpect(jsonPath("$.tipo").value("PF"));
    }

    @Test
    void criaPessoaJuridicaEAtualizaDados() throws Exception {
        long id = criarOk(adminA, "{\"tipo\":\"PJ\",\"nome\":\"Tech Ltda\",\"documento\":\"" + CNPJ_A + "\"}");

        mvc.perform(put("/api/v1/clientes/" + id).header("Authorization", "Bearer " + userA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tipo\":\"PJ\",\"nome\":\"Tech Solutions Ltda\",\"documento\":\"11222333000181\","
                                + "\"email\":\"contato@tech.com\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Tech Solutions Ltda"))
                .andExpect(jsonPath("$.email").value("contato@tech.com"));
    }

    @Test
    void listaComBuscaPorNomeDocumentoComMascaraEFiltroDeTipo() throws Exception {
        criarOk(adminA, pf("Maria Souza", CPF_A));
        criarOk(adminA, "{\"tipo\":\"PJ\",\"nome\":\"Tech Ltda\",\"documento\":\"" + CNPJ_A + "\"}");

        consultar(userA, "/api/v1/clientes").andExpect(jsonPath("$.totalElements").value(2));
        consultar(userA, "/api/v1/clientes?busca=maria").andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].nome").value("Maria Souza"));
        consultar(userA, "/api/v1/clientes?busca=529.982").andExpect(jsonPath("$.totalElements").value(1));
        consultar(userA, "/api/v1/clientes?tipo=PJ").andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].nome").value("Tech Ltda"));
        // Caracteres curinga da busca nao viram "qualquer coisa".
        consultar(userA, "/api/v1/clientes?busca=_").andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void paginaOsResultadosEmOrdemAlfabetica() throws Exception {
        criarOk(adminA, pf("Carlos", CPF_A));
        criarOk(adminA, pf("Ana", "11144477735"));

        consultar(userA, "/api/v1/clientes?size=1&page=0").andExpect(jsonPath("$.content[0].nome").value("Ana"))
                .andExpect(jsonPath("$.totalPages").value(2));
        consultar(userA, "/api/v1/clientes?size=1&page=1").andExpect(jsonPath("$.content[0].nome").value("Carlos"));
    }

    // ---------- entradas invalidas ----------

    @Test
    void rejeitaCpfCnpjInvalidosEDadosObrigatorios() throws Exception {
        criar(userA, pf("Fulano", "529.982.247-24")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("CPF invalido"));
        criar(userA, "{\"tipo\":\"PJ\",\"nome\":\"X\",\"documento\":\"11.222.333/0001-80\"}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("CNPJ invalido"));
        criar(userA, "{\"nome\":\"X\",\"documento\":\"" + CPF_A + "\"}").andExpect(status().isBadRequest());
        criar(userA, pf("", CPF_A)).andExpect(status().isBadRequest());
        criar(userA, "{\"tipo\":\"PF\",\"nome\":\"X\",\"documento\":\"" + CPF_A + "\",\"email\":\"nao-e-email\"}")
                .andExpect(status().isBadRequest());
        criar(userA, "{\"tipo\":\"PF\",\"nome\":\"X\",\"documento\":\"" + CPF_A + "\",\"cep\":\"123\"}")
                .andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM cliente WHERE tenant_id = ?", Integer.class, tenantA)).isZero();
    }

    @Test
    void documentoDuplicadoNoMesmoTenantRetorna409MasOutroTenantPodeUsar() throws Exception {
        criarOk(adminA, pf("Maria", CPF_A));
        criar(userA, pf("Outra Maria", "52998224725")).andExpect(status().isConflict());

        // O mesmo CPF e permitido em outro escritorio.
        criar(adminB, pf("Maria", CPF_A)).andExpect(status().isCreated());
    }

    @Test
    void atualizarParaDocumentoDeOutroClienteRetorna409() throws Exception {
        criarOk(adminA, pf("Maria", CPF_A));
        long outro = criarOk(adminA, pf("Jose", "11144477735"));

        mvc.perform(put("/api/v1/clientes/" + outro).header("Authorization", "Bearer " + adminA)
                        .contentType(MediaType.APPLICATION_JSON).content(pf("Jose", CPF_A)))
                .andExpect(status().isConflict());
    }

    // ---------- permissoes ----------

    @Test
    void soAdminInativaEReativaECuidaDoFiltroDeStatus() throws Exception {
        long id = criarOk(userA, pf("Maria", CPF_A));

        mvc.perform(post("/api/v1/clientes/" + id + "/inativar").header("Authorization", "Bearer " + userA))
                .andExpect(status().isForbidden());
        assertThat(jdbc.queryForObject("SELECT ativo FROM cliente WHERE id = ?", Boolean.class, id)).isTrue();

        mvc.perform(post("/api/v1/clientes/" + id + "/inativar").header("Authorization", "Bearer " + adminA))
                .andExpect(status().isOk()).andExpect(jsonPath("$.ativo").value(false));
        consultar(userA, "/api/v1/clientes?ativo=true").andExpect(jsonPath("$.totalElements").value(0));
        consultar(userA, "/api/v1/clientes?ativo=false").andExpect(jsonPath("$.totalElements").value(1));

        mvc.perform(post("/api/v1/clientes/" + id + "/reativar").header("Authorization", "Bearer " + userA))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/clientes/" + id + "/reativar").header("Authorization", "Bearer " + adminA))
                .andExpect(status().isOk()).andExpect(jsonPath("$.ativo").value(true));
    }

    @Test
    void resumoContaSoOsClientesDoProprioTenant() throws Exception {
        long id = criarOk(adminA, pf("Maria", CPF_A));
        criarOk(adminA, "{\"tipo\":\"PJ\",\"nome\":\"Tech Ltda\",\"documento\":\"" + CNPJ_A + "\"}");
        mvc.perform(post("/api/v1/clientes/" + id + "/inativar").header("Authorization", "Bearer " + adminA))
                .andExpect(status().isOk());
        criarOk(adminB, pf("Cliente do B", "11144477735"));

        consultar(userA, "/api/v1/clientes/resumo")
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.ativos").value(1))
                .andExpect(jsonPath("$.inativos").value(1))
                .andExpect(jsonPath("$.pessoasFisicas").value(1))
                .andExpect(jsonPath("$.pessoasJuridicas").value(1));
        consultar(adminB, "/api/v1/clientes/resumo").andExpect(jsonPath("$.total").value(1));
        mvc.perform(get("/api/v1/clientes/resumo")).andExpect(status().isUnauthorized());
    }

    @Test
    void semLoginRetorna401() throws Exception {
        mvc.perform(get("/api/v1/clientes")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/clientes").contentType(MediaType.APPLICATION_JSON).content(pf("X", CPF_A)))
                .andExpect(status().isUnauthorized());
    }

    // ---------- isolamento entre tenants ----------

    @Test
    void outroTenantNaoVeEditaNemInativaClienteAlheio() throws Exception {
        long id = criarOk(adminA, pf("Segredo do escritorio A", CPF_A));

        consultar(adminB, "/api/v1/clientes/" + id).andExpect(status().isNotFound());
        mvc.perform(put("/api/v1/clientes/" + id).header("Authorization", "Bearer " + adminB)
                        .contentType(MediaType.APPLICATION_JSON).content(pf("Invadido", "11144477735")))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/clientes/" + id + "/inativar").header("Authorization", "Bearer " + adminB))
                .andExpect(status().isNotFound());
        consultar(adminB, "/api/v1/clientes").andExpect(jsonPath("$.totalElements").value(0));
        consultar(adminB, "/api/v1/clientes?busca=segredo").andExpect(jsonPath("$.totalElements").value(0));
        consultar(adminB, "/api/v1/clientes?busca=52998224725").andExpect(jsonPath("$.totalElements").value(0));

        // Nada mudou para o dono.
        consultar(adminA, "/api/v1/clientes/" + id).andExpect(jsonPath("$.nome").value("Segredo do escritorio A"))
                .andExpect(jsonPath("$.ativo").value(true));
    }
}
