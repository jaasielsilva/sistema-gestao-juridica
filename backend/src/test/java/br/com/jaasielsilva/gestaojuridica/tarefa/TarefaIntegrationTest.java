package br.com.jaasielsilva.gestaojuridica.tarefa;

import br.com.jaasielsilva.gestaojuridica.ModuloIntegrationTestBase;
import br.com.jaasielsilva.gestaojuridica.shared.ClockConfig;
import br.com.jaasielsilva.gestaojuridica.user.Papel;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Quadro Kanban de tarefas contra SQL Server real: movimentos, vinculos, permissoes e isolamento entre tenants. */
class TarefaIntegrationTest extends ModuloIntegrationTestBase {

    private final LocalDate hoje = LocalDate.now(ClockConfig.FUSO);

    private Conta adminA;
    private Conta userA;
    private Conta outroUserA;
    private Conta adminB;
    private long processoA;

    @BeforeEach
    void cenario() throws Exception {
        long tenantA = novoTenant("Escritorio A");
        long tenantB = novoTenant("Escritorio B");
        adminA = novaConta(tenantA, Papel.ADMIN, "Admin A");
        userA = novaConta(tenantA, Papel.USER, "Usuario A");
        outroUserA = novaConta(tenantA, Papel.USER, "Outro Usuario A");
        adminB = novaConta(tenantB, Papel.ADMIN, "Admin B");
        processoA = criarProcesso(adminA, criarCliente(adminA, "Cliente A", "529.982.247-25"), cnj(1));
    }

    private Map<String, Object> tarefa(String titulo) {
        Map<String, Object> m = new HashMap<>();
        m.put("titulo", titulo);
        m.put("prioridade", "MEDIA");
        return m;
    }

    private long criarTarefa(Conta conta, String titulo) throws Exception {
        return idDe(enviar(conta, "/api/v1/tarefas", tarefa(titulo)).andExpect(status().isCreated()));
    }

    private void mover(Conta conta, long id, String status, int posicao) throws Exception {
        alterar(conta, "/api/v1/tarefas/" + id + "/mover", Map.of("status", status, "posicao", posicao))
                .andExpect(status().isOk());
    }

    /** Ids da coluna, na ordem em que o quadro as devolve. */
    private List<Long> coluna(Conta conta, String status) throws Exception {
        String corpo = consultar(conta, "/api/v1/tarefas").andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString();
        List<Long> ids = new ArrayList<>();
        for (JsonNode t : json.readTree(corpo)) {
            if (status.equals(t.path("status").asText())) {
                ids.add(t.path("id").asLong());
            }
        }
        return ids;
    }

    @Test
    void criaTarefaLigadaAoProcessoEAoResponsavel() throws Exception {
        Map<String, Object> corpo = tarefa("Protocolar contestacao");
        corpo.put("descricao", "Revisar documentos antes");
        corpo.put("prioridade", "ALTA");
        corpo.put("prazo", hoje.plusDays(3).toString());
        corpo.put("processoId", processoA);
        corpo.put("responsavelId", outroUserA.userId());

        long id = idDe(enviar(userA, "/api/v1/tarefas", corpo)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("A_FAZER"))
                .andExpect(jsonPath("$.prioridade").value("ALTA"))
                .andExpect(jsonPath("$.atrasada").value(false))
                .andExpect(jsonPath("$.processoId").value(processoA))
                .andExpect(jsonPath("$.clienteNome").value("Cliente A"))
                .andExpect(jsonPath("$.responsavelNome").value("Outro Usuario A")));

        assertThat(jdbc.queryForObject("SELECT tenant_id FROM tarefa WHERE id = ?", Long.class, id)).isEqualTo(adminA.tenantId());
        assertThat(jdbc.queryForObject("SELECT criado_por_id FROM tarefa WHERE id = ?", Long.class, id)).isEqualTo(userA.userId());
    }

    @Test
    void tarefaComPrazoPassadoApareceAtrasadaAteSerConcluida() throws Exception {
        Map<String, Object> corpo = tarefa("Enviar documentos");
        corpo.put("prazo", hoje.minusDays(2).toString());
        long id = idDe(enviar(userA, "/api/v1/tarefas", corpo).andExpect(jsonPath("$.atrasada").value(true)));

        alterar(userA, "/api/v1/tarefas/" + id + "/mover", Map.of("status", "CONCLUIDA", "posicao", 0))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.atrasada").value(false))
                .andExpect(jsonPath("$.concluidaEm").isNotEmpty());
    }

    @Test
    void tarefasNovasEntramNoFimDaColuna() throws Exception {
        long a = criarTarefa(userA, "A");
        long b = criarTarefa(userA, "B");
        long c = criarTarefa(userA, "C");
        assertThat(coluna(userA, "A_FAZER")).containsExactly(a, b, c);
    }

    @Test
    void moveParaOutraColunaNaPosicaoPedidaERenumera() throws Exception {
        long a = criarTarefa(userA, "A");
        long b = criarTarefa(userA, "B");
        long c = criarTarefa(userA, "C");

        mover(userA, a, "EM_ANDAMENTO", 0);
        mover(userA, b, "EM_ANDAMENTO", 0); // entra no topo, na frente de A
        mover(userA, c, "EM_ANDAMENTO", 99); // posicao alem do fim vai para o fim

        assertThat(coluna(userA, "EM_ANDAMENTO")).containsExactly(b, a, c);
        assertThat(coluna(userA, "A_FAZER")).isEmpty();
        assertThat(jdbc.queryForList("SELECT ordem FROM tarefa WHERE status = 'EM_ANDAMENTO' AND tenant_id = ? ORDER BY ordem",
                Integer.class, adminA.tenantId())).containsExactly(0, 1, 2);
    }

    @Test
    void reordenaDentroDaMesmaColuna() throws Exception {
        long a = criarTarefa(userA, "A");
        long b = criarTarefa(userA, "B");
        long c = criarTarefa(userA, "C");

        mover(userA, c, "A_FAZER", 0);
        assertThat(coluna(userA, "A_FAZER")).containsExactly(c, a, b);
        mover(userA, c, "A_FAZER", 1);
        assertThat(coluna(userA, "A_FAZER")).containsExactly(a, c, b);
    }

    @Test
    void sairDeConcluidaLimpaADataDeConclusao() throws Exception {
        long id = criarTarefa(userA, "A");
        mover(userA, id, "CONCLUIDA", 0);
        assertThat(jdbc.queryForObject("SELECT concluida_em FROM tarefa WHERE id = ?", java.sql.Timestamp.class, id)).isNotNull();

        mover(userA, id, "AGUARDANDO", 0);
        assertThat(jdbc.queryForObject("SELECT concluida_em FROM tarefa WHERE id = ?", java.sql.Timestamp.class, id)).isNull();
    }

    @Test
    void concluidasAntigasSaemDoQuadro() throws Exception {
        long recente = criarTarefa(userA, "Recente");
        long antiga = criarTarefa(userA, "Antiga");
        mover(userA, recente, "CONCLUIDA", 0);
        mover(userA, antiga, "CONCLUIDA", 0);
        jdbc.update("UPDATE tarefa SET concluida_em = DATEADD(day, -45, SYSUTCDATETIME()) WHERE id = ?", antiga);

        assertThat(coluna(userA, "CONCLUIDA")).containsExactly(recente);
    }

    @Test
    void filtraPorResponsavelEPorProcesso() throws Exception {
        Map<String, Object> minha = tarefa("Minha");
        minha.put("responsavelId", userA.userId());
        minha.put("processoId", processoA);
        enviar(adminA, "/api/v1/tarefas", minha).andExpect(status().isCreated());
        criarTarefa(adminA, "Sem dono");

        consultar(adminA, "/api/v1/tarefas").andExpect(jsonPath("$.length()").value(2));
        consultar(adminA, "/api/v1/tarefas?responsavelId=" + userA.userId()).andExpect(jsonPath("$.length()").value(1));
        consultar(adminA, "/api/v1/tarefas?processoId=" + processoA).andExpect(jsonPath("$.length()").value(1));
        consultar(adminA, "/api/v1/tarefas?responsavelId=" + outroUserA.userId()).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void buscaPorTituloDescricaoClienteENumeroDoProcesso() throws Exception {
        Map<String, Object> ligada = tarefa("Protocolar contestacao");
        ligada.put("descricao", "Revisar a procuracao antes");
        ligada.put("processoId", processoA);
        enviar(userA, "/api/v1/tarefas", ligada).andExpect(status().isCreated());
        criarTarefa(userA, "Ligar para o perito");

        consultar(userA, "/api/v1/tarefas?busca=PROTOCOLAR").andExpect(jsonPath("$.length()").value(1));
        consultar(userA, "/api/v1/tarefas?busca=procuracao").andExpect(jsonPath("$.length()").value(1));
        consultar(userA, "/api/v1/tarefas?busca=cliente a").andExpect(jsonPath("$.length()").value(1));
        consultar(userA, "/api/v1/tarefas?busca=0000001").andExpect(jsonPath("$.length()").value(1));
        consultar(userA, "/api/v1/tarefas?busca=perito").andExpect(jsonPath("$.length()").value(1));
        consultar(userA, "/api/v1/tarefas?busca=zzz").andExpect(jsonPath("$.length()").value(0));
        consultar(userA, "/api/v1/tarefas").andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void editaTarefaSemMudarDeColuna() throws Exception {
        long id = criarTarefa(userA, "Original");
        mover(userA, id, "AGUARDANDO", 0);

        Map<String, Object> novo = tarefa("Editada");
        novo.put("prioridade", "URGENTE");
        novo.put("status", "A_FAZER"); // ignorado: a coluna so muda por /mover
        alterar(userA, "/api/v1/tarefas/" + id, novo)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.titulo").value("Editada"))
                .andExpect(jsonPath("$.prioridade").value("URGENTE"))
                .andExpect(jsonPath("$.status").value("AGUARDANDO"));
    }

    @Test
    void entradasInvalidasSaoRecusadas() throws Exception {
        Map<String, Object> semTitulo = tarefa(" ");
        enviar(userA, "/api/v1/tarefas", semTitulo).andExpect(status().isBadRequest());
        Map<String, Object> semPrioridade = tarefa("X");
        semPrioridade.remove("prioridade");
        enviar(userA, "/api/v1/tarefas", semPrioridade).andExpect(status().isBadRequest());
        Map<String, Object> processoInexistente = tarefa("X");
        processoInexistente.put("processoId", 999999L);
        enviar(userA, "/api/v1/tarefas", processoInexistente).andExpect(status().isNotFound());

        long id = criarTarefa(userA, "A");
        alterar(userA, "/api/v1/tarefas/" + id + "/mover", Map.of("status", "NAO_EXISTE", "posicao", 0))
                .andExpect(status().isBadRequest());
        alterar(userA, "/api/v1/tarefas/" + id + "/mover", Map.of("status", "A_FAZER", "posicao", -1))
                .andExpect(status().isBadRequest());
    }

    @Test
    void soQuemCriouOuOAdminApaga() throws Exception {
        long id = criarTarefa(userA, "A");

        apagar(outroUserA, "/api/v1/tarefas/" + id).andExpect(status().isForbidden());
        consultar(userA, "/api/v1/tarefas/" + id).andExpect(status().isOk());

        apagar(userA, "/api/v1/tarefas/" + id).andExpect(status().isNoContent());
        consultar(userA, "/api/v1/tarefas/" + id).andExpect(status().isNotFound());

        long outra = criarTarefa(userA, "B");
        apagar(adminA, "/api/v1/tarefas/" + outra).andExpect(status().isNoContent());
    }

    @Test
    void listaDeResponsaveisTrazSoColegasDoMesmoTenantSemDadosSensiveis() throws Exception {
        String corpo = consultar(userA, "/api/v1/usuarios/responsaveis").andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].email").doesNotExist())
                .andExpect(jsonPath("$[0].papel").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        assertThat(corpo).doesNotContain("Admin B");
    }

    @Test
    void semTokenNaoAcessa() throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/tarefas"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void isolamentoEntreTenants() throws Exception {
        long idA = criarTarefa(adminA, "Tarefa de A");
        long idB = criarTarefa(adminB, "Tarefa de B");

        assertThat(coluna(adminB, "A_FAZER")).containsExactly(idB);
        consultar(adminB, "/api/v1/tarefas/" + idA).andExpect(status().isNotFound());
        alterar(adminB, "/api/v1/tarefas/" + idA, tarefa("Invadida")).andExpect(status().isNotFound());
        alterar(adminB, "/api/v1/tarefas/" + idA + "/mover", Map.of("status", "CONCLUIDA", "posicao", 0))
                .andExpect(status().isNotFound());
        apagar(adminB, "/api/v1/tarefas/" + idA).andExpect(status().isNotFound());
        consultar(adminB, "/api/v1/tarefas?processoId=" + processoA).andExpect(jsonPath("$.length()").value(0));

        // B nao liga tarefa a processo nem a usuario de A
        Map<String, Object> comProcessoDeA = tarefa("X");
        comProcessoDeA.put("processoId", processoA);
        enviar(adminB, "/api/v1/tarefas", comProcessoDeA).andExpect(status().isNotFound());
        Map<String, Object> comUsuarioDeA = tarefa("X");
        comUsuarioDeA.put("responsavelId", userA.userId());
        enviar(adminB, "/api/v1/tarefas", comUsuarioDeA).andExpect(status().isBadRequest());

        // a busca de B nao enxerga a tarefa de A
        consultar(adminB, "/api/v1/tarefas?busca=Tarefa de A").andExpect(jsonPath("$.length()").value(0));
        consultar(adminB, "/api/v1/tarefas?busca=Tarefa de B").andExpect(jsonPath("$.length()").value(1));

        // e a lista de responsaveis de B so tem B
        consultar(adminB, "/api/v1/usuarios/responsaveis").andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(adminB.userId()));

        // a tarefa de A continua intacta
        consultar(adminA, "/api/v1/tarefas/" + idA).andExpect(jsonPath("$.titulo").value("Tarefa de A"));
    }
}
