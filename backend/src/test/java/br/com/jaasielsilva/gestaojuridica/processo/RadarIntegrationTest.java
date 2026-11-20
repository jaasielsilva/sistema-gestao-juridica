package br.com.jaasielsilva.gestaojuridica.processo;

import br.com.jaasielsilva.gestaojuridica.ModuloIntegrationTestBase;
import br.com.jaasielsilva.gestaojuridica.user.Papel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Radar de processos e mudanca de etapa contra SQL Server real, incluindo isolamento entre dois tenants. */
class RadarIntegrationTest extends ModuloIntegrationTestBase {

    private Conta adminA;
    private Conta userA;
    private Conta adminB;
    private long clienteA;
    private long fluindo;
    private long atencao;
    private long travado;
    private long encerrado;
    private long suspenso;
    private long processoB;

    @BeforeEach
    void cenario() throws Exception {
        long tenantA = novoTenant("Escritorio A");
        long tenantB = novoTenant("Escritorio B");
        adminA = novaConta(tenantA, Papel.ADMIN, "Admin A");
        userA = novaConta(tenantA, Papel.USER, "Usuario A");
        adminB = novaConta(tenantB, Papel.ADMIN, "Admin B");
        clienteA = criarCliente(adminA, "Cliente A", "529.982.247-25");

        fluindo = processoComIdade(1, 1);
        atencao = processoComIdade(2, 5);
        travado = processoComIdade(3, 10);
        encerrado = processoComIdade(4, 30);
        suspenso = processoComIdade(5, 30);
        jdbc.update("UPDATE processo SET situacao = 'ENCERRADO' WHERE id = ?", encerrado);
        jdbc.update("UPDATE processo SET situacao = 'SUSPENSO' WHERE id = ?", suspenso);

        processoB = criarProcesso(adminB, criarCliente(adminB, "Cliente B", "111.444.777-35"), cnj(1));
        envelhecer(processoB, 40);
    }

    private long processoComIdade(int sequencial, int dias) throws Exception {
        long id = criarProcesso(adminA, clienteA, cnj(sequencial));
        envelhecer(id, dias);
        return id;
    }

    private void envelhecer(long processoId, int dias) {
        jdbc.update("UPDATE processo SET etapa_atualizada_em = DATEADD(day, -?, SYSUTCDATETIME()) WHERE id = ?",
                dias, processoId);
    }

    @Test
    void resumoContaSoProcessosAtivosPorFaixaEPorEtapa() throws Exception {
        consultar(userA, "/api/v1/radar/resumo")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(3))
                .andExpect(jsonPath("$.travados").value(1))
                .andExpect(jsonPath("$.atencao").value(1))
                .andExpect(jsonPath("$.fluindo").value(1))
                .andExpect(jsonPath("$.porEtapa.ANALISE").value(3))
                .andExpect(jsonPath("$.porEtapa.CONTATO").value(0))
                .andExpect(jsonPath("$.porEtapa.PROTOCOLADO").value(0));
    }

    @Test
    void listaOsMaisParadosPrimeiroSemEncerradosNemSuspensos() throws Exception {
        consultar(userA, "/api/v1/radar")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content[0].id").value(travado))
                .andExpect(jsonPath("$.content[0].diasNaEtapa").value(10))
                .andExpect(jsonPath("$.content[0].faixa").value("TRAVADO"))
                .andExpect(jsonPath("$.content[0].clienteNome").value("Cliente A"))
                .andExpect(jsonPath("$.content[0].etapaRotulo").value("Análise"))
                .andExpect(jsonPath("$.content[1].id").value(atencao))
                .andExpect(jsonPath("$.content[1].faixa").value("ATENCAO"))
                .andExpect(jsonPath("$.content[2].id").value(fluindo))
                .andExpect(jsonPath("$.content[2].faixa").value("OK"));
    }

    @Test
    void filtraPorFaixaEPorEtapa() throws Exception {
        consultar(userA, "/api/v1/radar?faixa=TRAVADO").andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(travado));
        consultar(userA, "/api/v1/radar?faixa=ATENCAO").andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(atencao));
        consultar(userA, "/api/v1/radar?faixa=OK").andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(fluindo));
        consultar(userA, "/api/v1/radar?etapa=ANALISE").andExpect(jsonPath("$.totalElements").value(3));
        consultar(userA, "/api/v1/radar?etapa=RECURSO").andExpect(jsonPath("$.totalElements").value(0));
        consultar(userA, "/api/v1/radar?faixa=INVALIDA").andExpect(status().isBadRequest());
    }

    @Test
    void buscaPorNumeroClienteOuAdvogadoEcombinaComFaixa() throws Exception {
        consultar(userA, "/api/v1/radar?busca=CLIENTE a").andExpect(jsonPath("$.totalElements").value(3));
        consultar(userA, "/api/v1/radar?busca=advogado teste").andExpect(jsonPath("$.totalElements").value(3));
        consultar(userA, "/api/v1/radar?busca=0000003").andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(travado));
        consultar(userA, "/api/v1/radar?busca=52998224725").andExpect(jsonPath("$.totalElements").value(3));
        consultar(userA, "/api/v1/radar?busca=nao-existe").andExpect(jsonPath("$.totalElements").value(0));
        consultar(userA, "/api/v1/radar?busca=cliente a&faixa=TRAVADO").andExpect(jsonPath("$.totalElements").value(1));
        // processos encerrados/suspensos continuam fora do radar mesmo achados pela busca
        consultar(userA, "/api/v1/radar?busca=0000004").andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void mudarEtapaZeraOsDiasERefleteNoResumo() throws Exception {
        alterar(userA, "/api/v1/processos/" + travado + "/etapa", Map.of("etapa", "PROTOCOLADO"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.etapa").value("PROTOCOLADO"))
                .andExpect(jsonPath("$.etapaRotulo").value("Protocolado"));

        consultar(userA, "/api/v1/radar/resumo")
                .andExpect(jsonPath("$.travados").value(0))
                .andExpect(jsonPath("$.fluindo").value(2))
                .andExpect(jsonPath("$.porEtapa.PROTOCOLADO").value(1))
                .andExpect(jsonPath("$.porEtapa.ANALISE").value(2));
        consultar(userA, "/api/v1/radar?etapa=PROTOCOLADO")
                .andExpect(jsonPath("$.content[0].id").value(travado))
                .andExpect(jsonPath("$.content[0].diasNaEtapa").value(0));
    }

    @Test
    void repetirAEtapaAtualNaoReiniciaAContagem() throws Exception {
        alterar(userA, "/api/v1/processos/" + atencao + "/etapa", Map.of("etapa", "ANALISE")).andExpect(status().isOk());
        consultar(userA, "/api/v1/radar?faixa=ATENCAO").andExpect(jsonPath("$.content[0].diasNaEtapa").value(5));
    }

    @Test
    void etapaInvalidaOuAusenteEhRecusada() throws Exception {
        alterar(userA, "/api/v1/processos/" + travado + "/etapa", Map.of("etapa", "NAO_EXISTE"))
                .andExpect(status().isBadRequest());
        alterar(userA, "/api/v1/processos/" + travado + "/etapa", Map.of()).andExpect(status().isBadRequest());
        alterar(userA, "/api/v1/processos/999999/etapa", Map.of("etapa", "RECURSO")).andExpect(status().isNotFound());
    }

    @Test
    void semTokenNaoAcessa() throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/radar"))
                .andExpect(status().isUnauthorized());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/radar/resumo"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void isolamentoEntreTenants() throws Exception {
        // B ve so o seu processo (40 dias, travado) e nada de A
        consultar(adminB, "/api/v1/radar").andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(processoB));
        consultar(adminB, "/api/v1/radar/resumo").andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.travados").value(1))
                .andExpect(jsonPath("$.porEtapa.ANALISE").value(1));

        // a busca de B tambem nao alcanca os processos de A
        consultar(adminB, "/api/v1/radar?busca=Cliente A").andExpect(jsonPath("$.totalElements").value(0));
        consultar(adminB, "/api/v1/radar?busca=0000003").andExpect(jsonPath("$.totalElements").value(0));

        // B nao muda a etapa de um processo de A
        alterar(adminB, "/api/v1/processos/" + travado + "/etapa", Map.of("etapa", "RECURSO"))
                .andExpect(status().isNotFound());
        consultar(adminA, "/api/v1/radar?faixa=TRAVADO").andExpect(jsonPath("$.content[0].etapa").value("ANALISE"));
    }
}
