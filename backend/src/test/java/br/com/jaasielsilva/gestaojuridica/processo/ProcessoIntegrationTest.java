package br.com.jaasielsilva.gestaojuridica.processo;

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

/** Cadastro de processos contra SQL Server real: regras, permissoes e isolamento entre dois tenants. */
@SpringBootTest
@AutoConfigureMockMvc
class ProcessoIntegrationTest extends IntegrationTestBase {

    private static final String PASSWORD = "senha-forte-123";
    private static final String CPF_A = "529.982.247-25";
    private static final String CNPJ_A = "11.222.333/0001-81";
    private static final String CNJ_A = "0000009-15.2015.8.26.0558";
    private static final String CNJ_B = "0001234-71.2024.8.26.0100";

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
    private long clienteA;
    private long clientePjA;
    private long clienteB;

    @BeforeEach
    void cenario() throws Exception {
        tenantA = novoTenant("Escritorio A");
        tenantB = novoTenant("Escritorio B");
        adminA = loginDe(novoUsuario(tenantA, Papel.ADMIN));
        userA = loginDe(novoUsuario(tenantA, Papel.USER));
        adminB = loginDe(novoUsuario(tenantB, Papel.ADMIN));
        jdbc.update("DELETE FROM processo WHERE tenant_id IN (?, ?)", tenantA, tenantB);
        jdbc.update("DELETE FROM cliente WHERE tenant_id IN (?, ?)", tenantA, tenantB);
        clienteA = criarCliente(adminA, "{\"tipo\":\"PF\",\"nome\":\"Joao da Silva\",\"documento\":\"" + CPF_A + "\"}");
        clientePjA = criarCliente(adminA,
                "{\"tipo\":\"PJ\",\"nome\":\"Tech Solutions Ltda\",\"documento\":\"" + CNPJ_A + "\"}");
        clienteB = criarCliente(adminB, "{\"tipo\":\"PF\",\"nome\":\"Cliente do B\",\"documento\":\"11144477735\"}");
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

    private String processo(String numero, long clienteId, String tipo, String situacao) {
        return "{\"numero\":\"" + numero + "\",\"clienteId\":" + clienteId + ",\"tipo\":\"" + tipo
                + "\",\"areaJuridica\":\"Direito Civil\",\"tribunal\":\"TJSP - Comarca de Sao Paulo\","
                + "\"comarca\":\"Sao Paulo\",\"vara\":\"1ª Vara Civel\",\"advogadoNome\":\"Jasiel Miranda\","
                + "\"advogadoOab\":\"OAB/SP 123.456\",\"situacao\":\"" + situacao + "\","
                + "\"proximoPrazo\":\"2024-10-22\",\"proximoPrazoDescricao\":\"Contestacao\"}";
    }

    private ResultActions criar(String token, String body) throws Exception {
        return mvc.perform(post("/api/v1/processos").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private long criarOk(String token, String body) throws Exception {
        MvcResult r = criar(token, body).andExpect(status().isCreated()).andReturn();
        return json.readTree(r.getResponse().getContentAsString()).path("id").asLong();
    }

    private ResultActions consultar(String token, String path) throws Exception {
        return mvc.perform(get(path).header("Authorization", "Bearer " + token));
    }

    @Test
    void criaProcessoNormalizandoNumeroELigandoAoClienteDoTenant() throws Exception {
        MvcResult r = criar(userA, processo(CNJ_A, clienteA, "CIVIL", "EM_ANDAMENTO"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.numero").value("00000091520158260558"))
                .andExpect(jsonPath("$.clienteId").value(clienteA))
                .andExpect(jsonPath("$.clienteNome").value("Joao da Silva"))
                .andExpect(jsonPath("$.tipo").value("CIVIL"))
                .andExpect(jsonPath("$.situacao").value("EM_ANDAMENTO"))
                .andReturn();
        long id = json.readTree(r.getResponse().getContentAsString()).path("id").asLong();

        assertThat(jdbc.queryForObject("SELECT tenant_id FROM processo WHERE id = ?", Long.class, id)).isEqualTo(tenantA);
        consultar(userA, "/api/v1/processos/" + id).andExpect(status().isOk())
                .andExpect(jsonPath("$.advogadoNome").value("Jasiel Miranda"));
    }

    @Test
    void atualizaDadosDoProcesso() throws Exception {
        long id = criarOk(adminA, processo(CNJ_A, clienteA, "CIVIL", "EM_ANDAMENTO"));

        mvc.perform(put("/api/v1/processos/" + id).header("Authorization", "Bearer " + userA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(processo(CNJ_A, clientePjA, "EMPRESARIAL", "SUSPENSO")
                                .replace("Direito Civil", "Direito Empresarial")
                                .replace("Contestacao", "Manifestacao")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clienteId").value(clientePjA))
                .andExpect(jsonPath("$.tipo").value("EMPRESARIAL"))
                .andExpect(jsonPath("$.situacao").value("SUSPENSO"))
                .andExpect(jsonPath("$.areaJuridica").value("Direito Empresarial"));
    }

    @Test
    void listaComBuscaPorNumeroClienteAdvogadoEFiltros() throws Exception {
        criarOk(adminA, processo(CNJ_A, clienteA, "CIVIL", "EM_ANDAMENTO"));
        criarOk(adminA, processo(CNJ_B, clientePjA, "TRABALHISTA", "PENDENTE")
                .replace("Direito Civil", "Direito do Trabalho")
                .replace("TJSP - Comarca de Sao Paulo", "TRT 2ª Regiao"));

        consultar(userA, "/api/v1/processos").andExpect(jsonPath("$.totalElements").value(2));
        consultar(userA, "/api/v1/processos?busca=joao").andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].clienteNome").value("Joao da Silva"));
        consultar(userA, "/api/v1/processos?busca=0000009-15").andExpect(jsonPath("$.totalElements").value(1));
        consultar(userA, "/api/v1/processos?busca=jasiel").andExpect(jsonPath("$.totalElements").value(2));
        consultar(userA, "/api/v1/processos?tipo=TRABALHISTA").andExpect(jsonPath("$.totalElements").value(1));
        consultar(userA, "/api/v1/processos?situacao=EM_ANDAMENTO").andExpect(jsonPath("$.totalElements").value(1));
        consultar(userA, "/api/v1/processos?clienteId=" + clientePjA).andExpect(jsonPath("$.totalElements").value(1));
        consultar(userA, "/api/v1/processos?tribunal=TRT 2ª Regiao").andExpect(jsonPath("$.totalElements").value(1));
        consultar(userA, "/api/v1/processos?areaJuridica=Direito Civil").andExpect(jsonPath("$.totalElements").value(1));
        consultar(userA, "/api/v1/processos?busca=_").andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void rejeitaNumeroInvalidoClienteInexistenteECamposObrigatorios() throws Exception {
        criar(userA, processo("0000009-16.2015.8.26.0558", clienteA, "CIVIL", "EM_ANDAMENTO"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("Numero CNJ invalido"));
        criar(userA, processo(CNJ_A, 999999L, "CIVIL", "EM_ANDAMENTO")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Cliente nao encontrado"));
        criar(userA, processo(CNJ_A, clienteB, "CIVIL", "EM_ANDAMENTO")).andExpect(status().isNotFound());
        criar(userA, "{\"numero\":\"" + CNJ_A + "\",\"clienteId\":" + clienteA + "}").andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM processo WHERE tenant_id = ?", Integer.class, tenantA))
                .isZero();
    }

    @Test
    void numeroDuplicadoNoMesmoTenantRetorna409MasOutroTenantPodeUsar() throws Exception {
        criarOk(adminA, processo(CNJ_A, clienteA, "CIVIL", "EM_ANDAMENTO"));
        criar(userA, processo("00000091520158260558", clientePjA, "CIVIL", "PENDENTE")).andExpect(status().isConflict());
        criar(adminB, processo(CNJ_A, clienteB, "CIVIL", "EM_ANDAMENTO")).andExpect(status().isCreated());
    }

    @Test
    void atualizarParaNumeroDeOutroProcessoRetorna409() throws Exception {
        criarOk(adminA, processo(CNJ_A, clienteA, "CIVIL", "EM_ANDAMENTO"));
        long outro = criarOk(adminA, processo(CNJ_B, clientePjA, "CIVIL", "PENDENTE"));

        mvc.perform(put("/api/v1/processos/" + outro).header("Authorization", "Bearer " + adminA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(processo(CNJ_A, clientePjA, "CIVIL", "PENDENTE")))
                .andExpect(status().isConflict());
    }

    @Test
    void resumoContaSoOsProcessosDoProprioTenant() throws Exception {
        criarOk(adminA, processo(CNJ_A, clienteA, "CIVIL", "EM_ANDAMENTO"));
        criarOk(adminA, processo(CNJ_B, clientePjA, "CIVIL", "URGENTE"));
        criar(adminA, processo("0005678-50.2024.8.26.0100", clienteA, "CIVIL", "SUSPENSO"))
                .andExpect(status().isCreated());
        criarOk(adminB, processo(CNJ_A, clienteB, "CIVIL", "ENCERRADO"));

        consultar(userA, "/api/v1/processos/resumo")
                .andExpect(jsonPath("$.total").value(3))
                .andExpect(jsonPath("$.emAndamento").value(1))
                .andExpect(jsonPath("$.suspensos").value(1))
                .andExpect(jsonPath("$.encerrados").value(0))
                .andExpect(jsonPath("$.urgentes").value(1));
        consultar(adminB, "/api/v1/processos/resumo")
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.encerrados").value(1));
        mvc.perform(get("/api/v1/processos/resumo")).andExpect(status().isUnauthorized());
    }

    @Test
    void semLoginRetorna401() throws Exception {
        mvc.perform(get("/api/v1/processos")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/processos").contentType(MediaType.APPLICATION_JSON)
                        .content(processo(CNJ_A, clienteA, "CIVIL", "EM_ANDAMENTO")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void outroTenantNaoVeNemEditaProcessoAlheio() throws Exception {
        long id = criarOk(adminA, processo(CNJ_A, clienteA, "CIVIL", "EM_ANDAMENTO"));

        consultar(adminB, "/api/v1/processos/" + id).andExpect(status().isNotFound());
        mvc.perform(put("/api/v1/processos/" + id).header("Authorization", "Bearer " + adminB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(processo(CNJ_B, clienteB, "CIVIL", "ENCERRADO")))
                .andExpect(status().isNotFound());
        consultar(adminB, "/api/v1/processos").andExpect(jsonPath("$.totalElements").value(0));
        consultar(adminB, "/api/v1/processos?busca=joao").andExpect(jsonPath("$.totalElements").value(0));
        consultar(adminB, "/api/v1/processos?busca=00000091520158260558").andExpect(jsonPath("$.totalElements").value(0));

        consultar(adminA, "/api/v1/processos/" + id).andExpect(jsonPath("$.clienteNome").value("Joao da Silva"))
                .andExpect(jsonPath("$.situacao").value("EM_ANDAMENTO"));
    }
}
