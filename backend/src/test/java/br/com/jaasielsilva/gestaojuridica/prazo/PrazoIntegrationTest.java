package br.com.jaasielsilva.gestaojuridica.prazo;

import br.com.jaasielsilva.gestaojuridica.ModuloIntegrationTestBase;
import br.com.jaasielsilva.gestaojuridica.shared.ClockConfig;
import br.com.jaasielsilva.gestaojuridica.user.Papel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Prazos contra SQL Server real: regras, sincronia com o processo, permissoes e isolamento entre dois tenants. */
class PrazoIntegrationTest extends ModuloIntegrationTestBase {

    private final LocalDate hoje = LocalDate.now(ClockConfig.FUSO);

    private Conta adminA;
    private Conta userA;
    private Conta adminB;
    private long processoA;
    private long processoB;

    @BeforeEach
    void cenario() throws Exception {
        long tenantA = novoTenant("Escritorio A");
        long tenantB = novoTenant("Escritorio B");
        adminA = novaConta(tenantA, Papel.ADMIN, "Admin A");
        userA = novaConta(tenantA, Papel.USER, "Usuario A");
        adminB = novaConta(tenantB, Papel.ADMIN, "Admin B");
        processoA = criarProcesso(adminA, criarCliente(adminA, "Cliente A", "529.982.247-25"), cnj(1));
        processoB = criarProcesso(adminB, criarCliente(adminB, "Cliente B", "111.444.777-35"), cnj(1));
    }

    private Map<String, Object> prazo(long processoId, String tipo, LocalDate vencimento) {
        Map<String, Object> m = new HashMap<>();
        m.put("processoId", processoId);
        m.put("tipo", tipo);
        m.put("dataVencimento", vencimento.toString());
        return m;
    }

    private long criarPrazo(Conta conta, long processoId, String tipo, LocalDate vencimento) throws Exception {
        return idDe(enviar(conta, "/api/v1/prazos", prazo(processoId, tipo, vencimento)).andExpect(status().isCreated()));
    }

    @Test
    void criaPrazoCalculaSituacaoEAtualizaOProximoPrazoDoProcesso() throws Exception {
        enviar(userA, "/api/v1/prazos", prazo(processoA, "CONTESTACAO", hoje.plusDays(5)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tipoRotulo").value("Contestação"))
                .andExpect(jsonPath("$.categoria").value("DEFESA"))
                .andExpect(jsonPath("$.situacao").value("PROXIMO"))
                .andExpect(jsonPath("$.status").value("ABERTO"))
                .andExpect(jsonPath("$.processoId").value(processoA))
                .andExpect(jsonPath("$.clienteNome").value("Cliente A"));

        consultar(userA, "/api/v1/processos/" + processoA)
                .andExpect(jsonPath("$.proximoPrazo").value(hoje.plusDays(5).toString()))
                .andExpect(jsonPath("$.proximoPrazoDescricao").value("Contestação"));
    }

    @Test
    void situacoesHojeProximoVencidoCumpridoEResumo() throws Exception {
        criarPrazo(userA, processoA, "CONTESTACAO", hoje);
        criarPrazo(userA, processoA, "APELACAO", hoje.plusDays(3));
        criarPrazo(userA, processoA, "REPLICA", hoje.minusDays(2));
        long cumprido = criarPrazo(userA, processoA, "CUSTAS_INICIAIS", hoje.plusDays(1));
        enviar(userA, "/api/v1/prazos/" + cumprido + "/cumprir", null).andExpect(status().isOk());

        consultar(userA, "/api/v1/prazos/resumo")
                .andExpect(jsonPath("$.hoje").value(1))
                .andExpect(jsonPath("$.proximos").value(1))
                .andExpect(jsonPath("$.vencidos").value(1))
                .andExpect(jsonPath("$.cumpridosUltimos30Dias").value(1));

        consultar(userA, "/api/v1/prazos?situacao=VENCIDO")
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].tipo").value("REPLICA"))
                .andExpect(jsonPath("$.content[0].situacao").value("VENCIDO"));
        consultar(userA, "/api/v1/prazos?situacao=HOJE").andExpect(jsonPath("$.totalElements").value(1));
        consultar(userA, "/api/v1/prazos?situacao=CUMPRIDO").andExpect(jsonPath("$.totalElements").value(1));
        consultar(userA, "/api/v1/prazos?categoria=RECURSOS").andExpect(jsonPath("$.totalElements").value(1));
        consultar(userA, "/api/v1/prazos?dias=3").andExpect(jsonPath("$.totalElements").value(3)); // hoje, +1 (cumprido), +3
    }

    @Test
    void listaEmOrdemDeVencimentoComAbertosAntesDosCumpridos() throws Exception {
        long longe = criarPrazo(userA, processoA, "APELACAO", hoje.plusDays(10));
        long perto = criarPrazo(userA, processoA, "CONTESTACAO", hoje.plusDays(1));
        long baixado = criarPrazo(userA, processoA, "REPLICA", hoje.minusDays(5));
        enviar(userA, "/api/v1/prazos/" + baixado + "/cumprir", null).andExpect(status().isOk());

        consultar(userA, "/api/v1/prazos")
                .andExpect(jsonPath("$.content[0].id").value(perto))
                .andExpect(jsonPath("$.content[1].id").value(longe))
                .andExpect(jsonPath("$.content[2].id").value(baixado));
    }

    @Test
    void cumprirRecalculaProximoPrazoEReabrirVolta() throws Exception {
        long primeiro = criarPrazo(userA, processoA, "CONTESTACAO", hoje.plusDays(2));
        criarPrazo(userA, processoA, "APELACAO", hoje.plusDays(9));

        enviar(userA, "/api/v1/prazos/" + primeiro + "/cumprir", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.situacao").value("CUMPRIDO"))
                .andExpect(jsonPath("$.cumpridoEm").isNotEmpty());
        consultar(userA, "/api/v1/processos/" + processoA)
                .andExpect(jsonPath("$.proximoPrazo").value(hoje.plusDays(9).toString()));

        enviar(userA, "/api/v1/prazos/" + primeiro + "/cumprir", null).andExpect(status().isConflict());

        enviar(userA, "/api/v1/prazos/" + primeiro + "/reabrir", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ABERTO"))
                .andExpect(jsonPath("$.cumpridoEm").doesNotExist());
        consultar(userA, "/api/v1/processos/" + processoA)
                .andExpect(jsonPath("$.proximoPrazo").value(hoje.plusDays(2).toString()));
        enviar(userA, "/api/v1/prazos/" + primeiro + "/reabrir", null).andExpect(status().isConflict());
    }

    @Test
    void editarMudaDataETipoEResincroniza() throws Exception {
        long id = criarPrazo(userA, processoA, "CONTESTACAO", hoje.plusDays(2));
        Map<String, Object> novo = prazo(processoA, "EMBARGOS_DECLARACAO", hoje.plusDays(7));
        novo.put("descricao", "Embargos da sentenca");

        alterar(userA, "/api/v1/prazos/" + id, novo)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tipo").value("EMBARGOS_DECLARACAO"))
                .andExpect(jsonPath("$.descricao").value("Embargos da sentenca"));
        consultar(userA, "/api/v1/processos/" + processoA)
                .andExpect(jsonPath("$.proximoPrazo").value(hoje.plusDays(7).toString()))
                .andExpect(jsonPath("$.proximoPrazoDescricao").value("Embargos da sentenca"));
    }

    @Test
    void tipoOutroExigeDescricaoEEntradasInvalidasSaoRecusadas() throws Exception {
        enviar(userA, "/api/v1/prazos", prazo(processoA, "OUTRO", hoje.plusDays(1))).andExpect(status().isBadRequest());
        Map<String, Object> comDescricao = prazo(processoA, "OUTRO", hoje.plusDays(1));
        comDescricao.put("descricao", "Audiencia de conciliacao");
        enviar(userA, "/api/v1/prazos", comDescricao).andExpect(status().isCreated());

        enviar(userA, "/api/v1/prazos", prazo(processoA, "TIPO_QUE_NAO_EXISTE", hoje)).andExpect(status().isBadRequest());
        Map<String, Object> semData = prazo(processoA, "CONTESTACAO", hoje);
        semData.remove("dataVencimento");
        enviar(userA, "/api/v1/prazos", semData).andExpect(status().isBadRequest());
        enviar(userA, "/api/v1/prazos", prazo(999999L, "CONTESTACAO", hoje)).andExpect(status().isNotFound());
    }

    @Test
    void responsavelPrecisaSerDoMesmoTenant() throws Exception {
        Map<String, Object> proprio = prazo(processoA, "CONTESTACAO", hoje.plusDays(1));
        proprio.put("responsavelId", userA.userId());
        enviar(adminA, "/api/v1/prazos", proprio)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.responsavelId").value(userA.userId()))
                .andExpect(jsonPath("$.responsavelNome").value("Usuario A"));
        consultar(adminA, "/api/v1/prazos?responsavelId=" + userA.userId()).andExpect(jsonPath("$.totalElements").value(1));
        consultar(adminA, "/api/v1/prazos?responsavelId=" + adminA.userId()).andExpect(jsonPath("$.totalElements").value(0));

        Map<String, Object> deOutroTenant = prazo(processoA, "CONTESTACAO", hoje.plusDays(1));
        deOutroTenant.put("responsavelId", adminB.userId());
        enviar(adminA, "/api/v1/prazos", deOutroTenant).andExpect(status().isBadRequest());
    }

    @Test
    void soAdminExcluiEOProcessoVoltaAFicarSemPrazo() throws Exception {
        long id = criarPrazo(userA, processoA, "CONTESTACAO", hoje.plusDays(4));

        apagar(userA, "/api/v1/prazos/" + id).andExpect(status().isForbidden());
        apagar(adminA, "/api/v1/prazos/" + id).andExpect(status().isNoContent());
        consultar(adminA, "/api/v1/prazos/" + id).andExpect(status().isNotFound());
        consultar(adminA, "/api/v1/processos/" + processoA).andExpect(jsonPath("$.proximoPrazo").doesNotExist());
    }

    @Test
    void buscaPorClienteNumeroTipoEDescricaoSemDiferenciarAcentosNemMaiusculas() throws Exception {
        criarPrazo(userA, processoA, "CONTESTACAO", hoje.plusDays(1));
        Map<String, Object> comDescricao = prazo(processoA, "APELACAO", hoje.plusDays(2));
        comDescricao.put("descricao", "Recurso do laudo pericial");
        enviar(userA, "/api/v1/prazos", comDescricao).andExpect(status().isCreated());

        consultar(userA, "/api/v1/prazos?busca=CLIENTE a").andExpect(jsonPath("$.totalElements").value(2));
        consultar(userA, "/api/v1/prazos?busca=contestacao").andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].tipo").value("CONTESTACAO"));
        consultar(userA, "/api/v1/prazos?busca=LAUDO").andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].tipo").value("APELACAO"));
        consultar(userA, "/api/v1/prazos?busca=0000001").andExpect(jsonPath("$.totalElements").value(2));
        consultar(userA, "/api/v1/prazos?busca=52998224725").andExpect(jsonPath("$.totalElements").value(2)); // CPF do cliente
        consultar(userA, "/api/v1/prazos?busca=nada-disso").andExpect(jsonPath("$.totalElements").value(0));
        // "%" e "_" sao curingas do LIKE: tratados como texto literal, entao nao casam com tudo
        chamar(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/prazos").param("busca", "%"),
                userA, null).andExpect(jsonPath("$.totalElements").value(0));
        chamar(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/prazos").param("busca", "_"),
                userA, null).andExpect(jsonPath("$.totalElements").value(0));
        consultar(userA, "/api/v1/prazos?busca=apelacao&situacao=PROXIMO").andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void buscaNaoVazaPrazosDeOutroTenant() throws Exception {
        criarPrazo(adminA, processoA, "CONTESTACAO", hoje.plusDays(1));
        criarPrazo(adminB, processoB, "APELACAO", hoje.plusDays(2));

        consultar(adminB, "/api/v1/prazos?busca=Cliente A").andExpect(jsonPath("$.totalElements").value(0));
        consultar(adminB, "/api/v1/prazos?busca=contestacao").andExpect(jsonPath("$.totalElements").value(0));
        consultar(adminB, "/api/v1/prazos?busca=Cliente B").andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void catalogoDeTiposVemDoServidor() throws Exception {
        consultar(userA, "/api/v1/prazos/tipos")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(greaterThan(30)))
                .andExpect(jsonPath("$[?(@.codigo=='CONTESTACAO')].categoriaRotulo").value("Defesa e contraditório"));
    }

    @Test
    void semTokenNaoAcessa() throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/prazos"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void isolamentoEntreTenants() throws Exception {
        long idA = criarPrazo(adminA, processoA, "CONTESTACAO", hoje.plusDays(1));
        long idB = criarPrazo(adminB, processoB, "APELACAO", hoje.plusDays(2));
        assertThat(jdbc.queryForObject("SELECT tenant_id FROM prazo WHERE id = ?", Long.class, idA)).isEqualTo(adminA.tenantId());

        // B so enxerga o proprio prazo, tambem no resumo
        consultar(adminB, "/api/v1/prazos").andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(idB));
        consultar(adminB, "/api/v1/prazos/resumo").andExpect(jsonPath("$.proximos").value(1));

        // e nao alcanca o prazo de A por nenhum caminho
        consultar(adminB, "/api/v1/prazos/" + idA).andExpect(status().isNotFound());
        alterar(adminB, "/api/v1/prazos/" + idA, prazo(processoB, "APELACAO", hoje)).andExpect(status().isNotFound());
        enviar(adminB, "/api/v1/prazos/" + idA + "/cumprir", null).andExpect(status().isNotFound());
        enviar(adminB, "/api/v1/prazos/" + idA + "/reabrir", null).andExpect(status().isNotFound());
        apagar(adminB, "/api/v1/prazos/" + idA).andExpect(status().isNotFound());
        consultar(adminB, "/api/v1/prazos?processoId=" + processoA).andExpect(jsonPath("$.totalElements").value(0));

        // B nao cria prazo em processo de A
        enviar(adminB, "/api/v1/prazos", prazo(processoA, "CONTESTACAO", hoje)).andExpect(status().isNotFound());

        // o prazo de A continua intacto
        consultar(adminA, "/api/v1/prazos/" + idA).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ABERTO"));
    }
}
