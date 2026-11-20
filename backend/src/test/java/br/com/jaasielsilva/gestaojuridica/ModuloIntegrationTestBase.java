package br.com.jaasielsilva.gestaojuridica;

import br.com.jaasielsilva.gestaojuridica.mail.AccountMailer;
import br.com.jaasielsilva.gestaojuridica.processo.NumeroCnj;
import br.com.jaasielsilva.gestaojuridica.tenant.Tenant;
import br.com.jaasielsilva.gestaojuridica.tenant.TenantRepository;
import br.com.jaasielsilva.gestaojuridica.user.Papel;
import br.com.jaasielsilva.gestaojuridica.user.Usuario;
import br.com.jaasielsilva.gestaojuridica.user.UsuarioRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigInteger;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Base dos testes de modulos de negocio (prazo, radar, tarefa...): cria tenants e contas reais, faz login pela API e
 * oferece atalhos de chamada. Cada teste monta os seus dados; os helpers so evitam repetir o cenario de dois tenants.
 */
@SpringBootTest
@AutoConfigureMockMvc
public abstract class ModuloIntegrationTestBase extends IntegrationTestBase {

    protected static final String PASSWORD = "senha-forte-123";

    @Autowired protected MockMvc mvc;
    @Autowired protected ObjectMapper json;
    @Autowired protected UsuarioRepository usuarios;
    @Autowired protected TenantRepository tenants;
    @Autowired protected PasswordEncoder encoder;
    @Autowired protected JdbcTemplate jdbc;
    @MockitoBean protected AccountMailer mailer;

    /** Conta autenticada de um tenant. */
    protected record Conta(long tenantId, long userId, String token) {
    }

    protected long novoTenant(String nome) {
        long id = tenants.nextId();
        tenants.save(new Tenant(id, nome + " " + id));
        return id;
    }

    protected Conta novaConta(long tenantId, Papel papel, String nome) throws Exception {
        String email = "u" + UUID.randomUUID().toString().substring(0, 8) + "@teste.com";
        Usuario u = new Usuario(tenantId, nome, email, encoder.encode(PASSWORD), papel);
        u.marcarEmailVerificado();
        u = usuarios.save(u);
        MvcResult r = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", email, "senha", PASSWORD))))
                .andExpect(status().isOk()).andReturn();
        String token = json.readTree(r.getResponse().getContentAsString()).path("accessToken").asText();
        return new Conta(tenantId, u.getId(), token);
    }

    protected long criarCliente(Conta conta, String nome, String cpf) throws Exception {
        return idDe(chamar(post("/api/v1/clientes"), conta, Map.of("tipo", "PF", "nome", nome, "documento", cpf))
                .andExpect(status().isCreated()));
    }

    protected long criarProcesso(Conta conta, long clienteId, String cnj) throws Exception {
        return idDe(chamar(post("/api/v1/processos"), conta, Map.of(
                "numero", cnj, "clienteId", clienteId, "tipo", "CIVIL", "areaJuridica", "Direito Civil",
                "tribunal", "TJSP", "advogadoNome", "Advogado Teste", "situacao", "EM_ANDAMENTO"))
                .andExpect(status().isCreated()));
    }

    protected ResultActions chamar(MockHttpServletRequestBuilder req, Conta conta, Object body) throws Exception {
        req.header("Authorization", "Bearer " + conta.token());
        if (body != null) {
            req.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        }
        return mvc.perform(req);
    }

    protected ResultActions consultar(Conta conta, String path) throws Exception {
        return chamar(get(path), conta, null);
    }

    protected ResultActions enviar(Conta conta, String path, Object body) throws Exception {
        return chamar(post(path), conta, body);
    }

    protected ResultActions alterar(Conta conta, String path, Object body) throws Exception {
        return chamar(put(path), conta, body);
    }

    protected ResultActions apagar(Conta conta, String path) throws Exception {
        return chamar(delete(path), conta, null);
    }

    protected long idDe(ResultActions r) throws Exception {
        return json.readTree(r.andReturn().getResponse().getContentAsString()).path("id").asLong();
    }

    /** Numero CNJ valido (com mascara) para o sequencial dado: o digito verificador e calculado aqui. */
    protected static String cnj(int sequencial) {
        String corpo = String.format("%07d", sequencial) + "2024" + "8" + "26" + "0100";
        int dv = 98 - new BigInteger(corpo + "00").mod(BigInteger.valueOf(97)).intValue();
        String digitos = corpo.substring(0, 7) + String.format("%02d", dv) + corpo.substring(7);
        if (!NumeroCnj.valido(digitos)) {
            throw new IllegalStateException("CNJ de teste invalido: " + digitos);
        }
        return digitos.substring(0, 7) + "-" + digitos.substring(7, 9) + "." + digitos.substring(9, 13) + "."
                + digitos.charAt(13) + "." + digitos.substring(14, 16) + "." + digitos.substring(16);
    }
}
