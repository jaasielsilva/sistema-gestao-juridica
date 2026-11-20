package br.com.jaasielsilva.gestaojuridica.processo;

import br.com.jaasielsilva.gestaojuridica.cliente.Cliente;
import br.com.jaasielsilva.gestaojuridica.cliente.Documento;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Radar de processos: onde cada processo ativo esta no fluxo e ha quantos dias esta parado la.
 * "Ativo" = em andamento, pendente ou urgente (encerrado e suspenso ficam de fora: nao se espera movimento deles).
 */
@Service
public class RadarService {

    static final List<SituacaoProcesso> SITUACOES_ATIVAS =
            List.of(SituacaoProcesso.EM_ANDAMENTO, SituacaoProcesso.PENDENTE, SituacaoProcesso.URGENTE);
    static final int MAX_PAGE_SIZE = 100;

    private final ProcessoRepository processos;
    private final Clock clock;

    public RadarService(ProcessoRepository processos, Clock clock) {
        this.processos = processos;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PageResponse<RadarItem> listar(RadarFaixa faixa, EtapaProcesso etapa, String busca, int page, int size) {
        Instant agora = Instant.now(clock);
        var pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
                Sort.by("etapaAtualizadaEm").ascending().and(Sort.by("id").ascending()));
        var spec = Specification.where(ativos(faixa, etapa, agora)).and(comBusca(busca)).and(comCliente());
        return PageResponse.of(processos.findAll(spec, pageable), p -> RadarItem.of(p, agora));
    }

    @Transactional(readOnly = true)
    public RadarResumo resumo() {
        Instant agora = Instant.now(clock);
        long travados = processos.count(ativos(RadarFaixa.TRAVADO, null, agora));
        long atencao = processos.count(ativos(RadarFaixa.ATENCAO, null, agora));
        long fluindo = processos.count(ativos(RadarFaixa.OK, null, agora));

        Map<EtapaProcesso, Long> porEtapa = new EnumMap<>(EtapaProcesso.class);
        for (EtapaProcesso e : EtapaProcesso.values()) {
            porEtapa.put(e, 0L);
        }
        for (Object[] linha : processos.contarPorEtapa(SITUACOES_ATIVAS)) {
            porEtapa.put((EtapaProcesso) linha[0], (Long) linha[1]);
        }
        return new RadarResumo(travados + atencao + fluindo, travados, atencao, fluindo, porEtapa);
    }

    /** Processos ativos, opcionalmente de uma faixa e/ou etapa. O tenant e aplicado pelo Hibernate (@TenantId). */
    private static Specification<Processo> ativos(RadarFaixa faixa, EtapaProcesso etapa, Instant agora) {
        return (root, query, cb) -> {
            List<Predicate> and = new ArrayList<>();
            and.add(root.get("situacao").in(SITUACOES_ATIVAS));
            if (etapa != null) {
                and.add(cb.equal(root.get("etapa"), etapa));
            }
            if (faixa != null) {
                // Dias inteiros na etapa: >= 8 travado, 4..7 atencao, < 4 ok. Comparado com datas de corte (usa o indice).
                Instant corteTravado = agora.minus(RadarFaixa.DIAS_TRAVADO, ChronoUnit.DAYS);
                Instant corteAtencao = agora.minus(RadarFaixa.DIAS_ATENCAO, ChronoUnit.DAYS);
                switch (faixa) {
                    case TRAVADO -> and.add(cb.lessThanOrEqualTo(root.get("etapaAtualizadaEm"), corteTravado));
                    case ATENCAO -> {
                        and.add(cb.greaterThan(root.get("etapaAtualizadaEm"), corteTravado));
                        and.add(cb.lessThanOrEqualTo(root.get("etapaAtualizadaEm"), corteAtencao));
                    }
                    case OK -> and.add(cb.greaterThan(root.get("etapaAtualizadaEm"), corteAtencao));
                }
            }
            return cb.and(and.toArray(new Predicate[0]));
        };
    }

    /** Busca por numero do processo, cliente (nome ou documento) ou advogado; sem diferenciar maiusculas. */
    private static Specification<Processo> comBusca(String busca) {
        return (root, query, cb) -> {
            if (busca == null || busca.isBlank()) {
                return cb.conjunction();
            }
            var cliente = root.<Processo, Cliente>join("cliente", JoinType.INNER);
            String termo = "%" + busca.trim().toLowerCase().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
            List<Predicate> or = new ArrayList<>();
            or.add(cb.like(cb.lower(root.get("numero")), termo, '\\'));
            or.add(cb.like(cb.lower(root.get("advogadoNome")), termo, '\\'));
            or.add(cb.like(cb.lower(cliente.get("nome")), termo, '\\'));
            String digitos = Documento.somenteDigitos(busca);
            if (!digitos.isEmpty()) {
                or.add(cb.like(root.get("numero"), "%" + digitos + "%"));
                or.add(cb.like(cliente.get("documento"), "%" + digitos + "%"));
            }
            return cb.or(or.toArray(new Predicate[0]));
        };
    }

    /** Traz o cliente junto na listagem (evita N+1); na consulta de contagem nao faz fetch. */
    private static Specification<Processo> comCliente() {
        return (root, query, cb) -> {
            if (query != null && query.getResultType() != Long.class && query.getResultType() != long.class) {
                root.<Processo, Cliente>fetch("cliente", JoinType.INNER);
            }
            return cb.conjunction();
        };
    }
}
