package br.com.jaasielsilva.gestaojuridica.prazo;

import br.com.jaasielsilva.gestaojuridica.cliente.Cliente;
import br.com.jaasielsilva.gestaojuridica.cliente.Documento;
import br.com.jaasielsilva.gestaojuridica.processo.PageResponse;
import br.com.jaasielsilva.gestaojuridica.processo.Processo;
import br.com.jaasielsilva.gestaojuridica.processo.ProcessoRepository;
import br.com.jaasielsilva.gestaojuridica.shared.BusinessException;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Service
public class PrazoService {

    static final int MAX_PAGE_SIZE = 100;
    private static final int TAMANHO_DESCRICAO_PROCESSO = 200;

    private final PrazoRepository prazos;
    private final ProcessoRepository processos;
    private final ResponsavelService responsaveis;
    private final Clock clock;

    public PrazoService(PrazoRepository prazos, ProcessoRepository processos, ResponsavelService responsaveis,
                        Clock clock) {
        this.prazos = prazos;
        this.processos = processos;
        this.responsaveis = responsaveis;
        this.clock = clock;
    }

    public List<TipoPrazoResponse> tipos() {
        return Arrays.stream(TipoPrazo.values()).map(TipoPrazoResponse::of).toList();
    }

    @Transactional(readOnly = true)
    public PageResponse<PrazoResponse> listar(SituacaoPrazo situacao, Integer dias, CategoriaPrazo categoria,
                                              TipoPrazo tipo, Long responsavelId, Long processoId, String busca,
                                              int page, int size) {
        LocalDate hoje = hoje();
        var pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
                Sort.by("status").ascending().and(Sort.by("dataVencimento").ascending()).and(Sort.by("id").ascending()));
        var spec = filtro(hoje, situacao, dias, categoria, tipo, responsavelId, processoId, busca);
        return PageResponse.of(prazos.findAll(spec, pageable), p -> PrazoResponse.of(p, hoje));
    }

    @Transactional(readOnly = true)
    public PrazoResumo resumo() {
        LocalDate hoje = hoje();
        Instant desde = hoje.minusDays(30).atStartOfDay(clock.getZone()).toInstant();
        return new PrazoResumo(
                prazos.countByStatusAndDataVencimento(StatusPrazo.ABERTO, hoje),
                prazos.countByStatusAndDataVencimentoGreaterThan(StatusPrazo.ABERTO, hoje),
                prazos.countByStatusAndDataVencimentoLessThan(StatusPrazo.ABERTO, hoje),
                prazos.countByStatusAndCumpridoEmGreaterThanEqual(StatusPrazo.CUMPRIDO, desde));
    }

    @Transactional(readOnly = true)
    public PrazoResponse buscar(Long id) {
        return resposta(obter(id));
    }

    @Transactional
    public PrazoResponse criar(PrazoRequest r) {
        Processo processo = processos.findById(r.processoId())
                .orElseThrow(() -> BusinessException.notFound("Processo nao encontrado"));
        String descricao = descricaoValida(r);
        Prazo p = prazos.saveAndFlush(new Prazo(processo, r.tipo(), descricao, r.dataVencimento(),
                responsaveis.obterOuNulo(r.responsavelId())));
        sincronizar(processo);
        return resposta(p);
    }

    @Transactional
    public PrazoResponse atualizar(Long id, PrazoRequest r) {
        Prazo p = obter(id);
        p.atualizar(r.tipo(), descricaoValida(r), r.dataVencimento(), responsaveis.obterOuNulo(r.responsavelId()));
        prazos.saveAndFlush(p);
        sincronizar(p.getProcesso());
        return resposta(p);
    }

    @Transactional
    public PrazoResponse cumprir(Long id) {
        Prazo p = obter(id);
        if (p.getStatus() == StatusPrazo.CUMPRIDO) {
            throw BusinessException.conflict("Prazo ja foi cumprido");
        }
        p.cumprir(Instant.now(clock));
        prazos.saveAndFlush(p);
        sincronizar(p.getProcesso());
        return resposta(p);
    }

    @Transactional
    public PrazoResponse reabrir(Long id) {
        Prazo p = obter(id);
        if (p.getStatus() == StatusPrazo.ABERTO) {
            throw BusinessException.conflict("Prazo ja esta aberto");
        }
        p.reabrir();
        prazos.saveAndFlush(p);
        sincronizar(p.getProcesso());
        return resposta(p);
    }

    @Transactional
    public void excluir(Long id) {
        Prazo p = obter(id);
        Processo processo = p.getProcesso();
        prazos.delete(p);
        prazos.flush();
        sincronizar(processo);
    }

    private Prazo obter(Long id) {
        return prazos.findById(id).orElseThrow(() -> BusinessException.notFound("Prazo nao encontrado"));
    }

    private PrazoResponse resposta(Prazo p) {
        return PrazoResponse.of(p, hoje());
    }

    private LocalDate hoje() {
        return LocalDate.now(clock);
    }

    private static String descricaoValida(PrazoRequest r) {
        String descricao = r.descricao() == null || r.descricao().isBlank() ? null : r.descricao().trim();
        if (r.tipo() == TipoPrazo.OUTRO && descricao == null) {
            throw BusinessException.badRequest("Descreva o prazo quando o tipo for \"Outro\"");
        }
        return descricao;
    }

    /** processo.proximo_prazo passa a refletir o prazo aberto mais proximo (ou fica vazio sem prazos abertos). */
    private void sincronizar(Processo processo) {
        var proximo = prazos.findFirstByProcessoIdAndStatusOrderByDataVencimentoAscIdAsc(processo.getId(),
                StatusPrazo.ABERTO);
        if (proximo.isEmpty()) {
            processo.sincronizarProximoPrazo(null, null);
        } else {
            Prazo p = proximo.get();
            String texto = p.getDescricao() != null ? p.getDescricao() : p.getTipo().getRotulo();
            if (texto.length() > TAMANHO_DESCRICAO_PROCESSO) {
                texto = texto.substring(0, TAMANHO_DESCRICAO_PROCESSO);
            }
            processo.sincronizarProximoPrazo(p.getDataVencimento(), texto);
        }
        processos.save(processo);
    }

    /** Filtros opcionais; o tenant e aplicado pelo Hibernate (@TenantId). */
    private static Specification<Prazo> filtro(LocalDate hoje, SituacaoPrazo situacao, Integer dias,
                                               CategoriaPrazo categoria, TipoPrazo tipo, Long responsavelId,
                                               Long processoId, String busca) {
        return (root, query, cb) -> {
            boolean listagem = query != null && query.getResultType() != Long.class && query.getResultType() != long.class;
            Join<Prazo, Processo> processo;
            Join<Processo, Cliente> cliente;
            if (listagem) {
                // Na listagem o processo e o cliente vem junto (sem N+1); na contagem so o join, sem fetch.
                processo = (Join<Prazo, Processo>) (Object) root.fetch("processo", JoinType.INNER);
                cliente = (Join<Processo, Cliente>) (Object) processo.fetch("cliente", JoinType.INNER);
                root.fetch("responsavel", JoinType.LEFT);
            } else {
                processo = root.join("processo", JoinType.INNER);
                cliente = processo.join("cliente", JoinType.INNER);
            }
            List<Predicate> and = new ArrayList<>();
            if (situacao != null) {
                switch (situacao) {
                    case HOJE -> {
                        and.add(cb.equal(root.get("status"), StatusPrazo.ABERTO));
                        and.add(cb.equal(root.get("dataVencimento"), hoje));
                    }
                    case PROXIMO -> {
                        and.add(cb.equal(root.get("status"), StatusPrazo.ABERTO));
                        and.add(cb.greaterThan(root.get("dataVencimento"), hoje));
                    }
                    case VENCIDO -> {
                        and.add(cb.equal(root.get("status"), StatusPrazo.ABERTO));
                        and.add(cb.lessThan(root.get("dataVencimento"), hoje));
                    }
                    case CUMPRIDO -> and.add(cb.equal(root.get("status"), StatusPrazo.CUMPRIDO));
                }
            }
            if (dias != null && dias >= 0) {
                and.add(cb.between(root.get("dataVencimento"), hoje, hoje.plusDays(dias)));
            }
            if (tipo != null) {
                and.add(cb.equal(root.get("tipo"), tipo));
            } else if (categoria != null) {
                and.add(root.get("tipo").in(TipoPrazo.daCategoria(categoria)));
            }
            if (responsavelId != null) {
                and.add(cb.equal(root.get("responsavel").get("id"), responsavelId));
            }
            if (processoId != null) {
                and.add(cb.equal(processo.get("id"), processoId));
            }
            if (busca != null && !busca.isBlank()) {
                String termo = "%" + escapar(busca.trim().toLowerCase()) + "%";
                List<Predicate> or = new ArrayList<>();
                or.add(cb.like(cb.lower(cliente.get("nome")), termo, '\\'));
                or.add(cb.like(cb.lower(root.get("descricao")), termo, '\\'));
                String digitos = Documento.somenteDigitos(busca);
                if (!digitos.isEmpty()) {
                    or.add(cb.like(processo.get("numero"), "%" + digitos + "%"));
                    or.add(cb.like(cliente.get("documento"), "%" + digitos + "%"));
                }
                List<TipoPrazo> tiposAchados = TipoPrazo.buscarPorRotulo(busca);
                if (!tiposAchados.isEmpty()) {
                    or.add(root.get("tipo").in(tiposAchados));
                }
                and.add(cb.or(or.toArray(new Predicate[0])));
            }
            return cb.and(and.toArray(new Predicate[0]));
        };
    }

    private static String escapar(String s) {
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
