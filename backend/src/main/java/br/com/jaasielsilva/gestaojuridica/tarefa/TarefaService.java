package br.com.jaasielsilva.gestaojuridica.tarefa;

import br.com.jaasielsilva.gestaojuridica.cliente.Cliente;
import br.com.jaasielsilva.gestaojuridica.cliente.Documento;
import br.com.jaasielsilva.gestaojuridica.processo.Processo;
import br.com.jaasielsilva.gestaojuridica.processo.ProcessoRepository;
import br.com.jaasielsilva.gestaojuridica.security.AuthPrincipal;
import br.com.jaasielsilva.gestaojuridica.shared.BusinessException;
import br.com.jaasielsilva.gestaojuridica.user.Papel;
import br.com.jaasielsilva.gestaojuridica.user.ResponsavelService;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

@Service
public class TarefaService {

    /** Concluidas so aparecem no quadro por este periodo; antes disso saem da tela (continuam no banco). */
    static final int DIAS_CONCLUIDAS_VISIVEIS = 30;
    /** Teto de cartoes devolvidos ao quadro. */
    static final int MAX_TAREFAS_NO_QUADRO = 500;

    private final TarefaRepository tarefas;
    private final ProcessoRepository processos;
    private final ResponsavelService responsaveis;
    private final Clock clock;

    public TarefaService(TarefaRepository tarefas, ProcessoRepository processos, ResponsavelService responsaveis,
                         Clock clock) {
        this.tarefas = tarefas;
        this.processos = processos;
        this.responsaveis = responsaveis;
        this.clock = clock;
    }

    /** Todas as tarefas abertas + concluidas recentes, ordenadas por coluna e posicao (o frontend agrupa por status). */
    @Transactional(readOnly = true)
    public List<TarefaResponse> quadro(Long responsavelId, Long processoId, String busca) {
        LocalDate hoje = LocalDate.now(clock);
        Instant limiteConcluidas = Instant.now(clock).minus(DIAS_CONCLUIDAS_VISIVEIS, ChronoUnit.DAYS);
        var pageable = PageRequest.of(0, MAX_TAREFAS_NO_QUADRO,
                Sort.by("status").ascending().and(Sort.by("ordem").ascending()).and(Sort.by("id").ascending()));
        return tarefas.findAll(filtro(responsavelId, processoId, limiteConcluidas, busca), pageable).getContent().stream()
                .map(t -> TarefaResponse.of(t, hoje))
                .toList();
    }

    @Transactional(readOnly = true)
    public TarefaResponse buscar(Long id) {
        return resposta(obter(id));
    }

    @Transactional
    public TarefaResponse criar(TarefaRequest r, AuthPrincipal autor) {
        StatusTarefa status = r.status() == null ? StatusTarefa.A_FAZER : r.status();
        Tarefa t = new Tarefa(r.titulo().trim(), texto(r.descricao()), status, r.prioridade(), r.prazo(),
                processoOuNulo(r.processoId()), responsaveis.obterOuNulo(r.responsavelId()), autor.userId(),
                tarefas.maiorOrdem(status) + 1, Instant.now(clock));
        return resposta(tarefas.saveAndFlush(t));
    }

    @Transactional
    public TarefaResponse atualizar(Long id, TarefaRequest r) {
        Tarefa t = obter(id);
        t.atualizar(r.titulo().trim(), texto(r.descricao()), r.prioridade(), r.prazo(),
                processoOuNulo(r.processoId()), responsaveis.obterOuNulo(r.responsavelId()));
        return resposta(tarefas.saveAndFlush(t));
    }

    /**
     * Move o cartao para uma coluna/posicao e renumera a coluna de destino (0..n-1). Concluidas ficam sempre no topo
     * da coluna (ordenadas por data de conclusao na tela), entao nao renumeram: a coluna cresce sem limite.
     */
    @Transactional
    public TarefaResponse mover(Long id, MoverTarefaRequest r) {
        Tarefa t = obter(id);
        t.moverPara(r.status(), Instant.now(clock));
        if (r.status() == StatusTarefa.CONCLUIDA) {
            t.definirOrdem(0);
        } else {
            List<Tarefa> coluna = new ArrayList<>(tarefas.findByStatusOrderByOrdemAscIdAsc(r.status()));
            coluna.removeIf(outra -> outra.getId().equals(t.getId()));
            coluna.add(Math.min(r.posicao(), coluna.size()), t);
            for (int i = 0; i < coluna.size(); i++) {
                coluna.get(i).definirOrdem(i);
            }
        }
        return resposta(tarefas.saveAndFlush(t));
    }

    /** Quem criou a tarefa ou um ADMIN do mesmo tenant pode apagar. */
    @Transactional
    public void excluir(Long id, AuthPrincipal usuario) {
        Tarefa t = obter(id);
        if (usuario.papel() != Papel.ADMIN && !t.getCriadoPorId().equals(usuario.userId())) {
            throw BusinessException.forbidden("Somente quem criou a tarefa ou um administrador pode exclui-la");
        }
        tarefas.delete(t);
    }

    private Tarefa obter(Long id) {
        return tarefas.findById(id).orElseThrow(() -> BusinessException.notFound("Tarefa nao encontrada"));
    }

    private Processo processoOuNulo(Long id) {
        if (id == null) {
            return null;
        }
        return processos.findById(id).orElseThrow(() -> BusinessException.notFound("Processo nao encontrado"));
    }

    private TarefaResponse resposta(Tarefa t) {
        return TarefaResponse.of(t, LocalDate.now(clock));
    }

    private static String texto(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }

    /** Filtros opcionais; o tenant e aplicado pelo Hibernate (@TenantId). */
    private static Specification<Tarefa> filtro(Long responsavelId, Long processoId, Instant limiteConcluidas,
                                                String busca) {
        return (root, query, cb) -> {
            boolean listagem = query != null && query.getResultType() != Long.class && query.getResultType() != long.class;
            Join<Tarefa, Processo> processo;
            Join<Processo, Cliente> cliente;
            if (listagem) {
                // Na listagem o processo e o cliente vem junto (sem N+1); na contagem so o join, sem fetch.
                processo = (Join<Tarefa, Processo>) (Object) root.fetch("processo", JoinType.LEFT);
                cliente = (Join<Processo, Cliente>) (Object) processo.fetch("cliente", JoinType.LEFT);
                root.fetch("responsavel", JoinType.LEFT);
            } else {
                processo = root.join("processo", JoinType.LEFT);
                cliente = processo.join("cliente", JoinType.LEFT);
            }
            List<Predicate> and = new ArrayList<>();
            and.add(cb.or(cb.notEqual(root.get("status"), StatusTarefa.CONCLUIDA),
                    cb.greaterThanOrEqualTo(root.get("concluidaEm"), limiteConcluidas)));
            if (responsavelId != null) {
                and.add(cb.equal(root.get("responsavel").get("id"), responsavelId));
            }
            if (processoId != null) {
                and.add(cb.equal(root.get("processo").get("id"), processoId));
            }
            if (busca != null && !busca.isBlank()) {
                String termo = "%" + busca.trim().toLowerCase().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
                List<Predicate> or = new ArrayList<>();
                or.add(cb.like(cb.lower(root.get("titulo")), termo, '\\'));
                or.add(cb.like(cb.lower(root.get("descricao")), termo, '\\'));
                or.add(cb.like(cb.lower(cliente.get("nome")), termo, '\\'));
                String digitos = Documento.somenteDigitos(busca);
                if (!digitos.isEmpty()) {
                    or.add(cb.like(processo.get("numero"), "%" + digitos + "%"));
                }
                and.add(cb.or(or.toArray(new Predicate[0])));
            }
            return cb.and(and.toArray(new Predicate[0]));
        };
    }
}
