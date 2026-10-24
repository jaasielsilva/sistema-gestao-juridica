package br.com.jaasielsilva.gestaojuridica.processo;

import br.com.jaasielsilva.gestaojuridica.cliente.Cliente;
import br.com.jaasielsilva.gestaojuridica.cliente.ClienteRepository;
import br.com.jaasielsilva.gestaojuridica.cliente.Documento;
import br.com.jaasielsilva.gestaojuridica.shared.BusinessException;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class ProcessoService {

    static final int MAX_PAGE_SIZE = 100;

    private final ProcessoRepository processos;
    private final ClienteRepository clientes;

    public ProcessoService(ProcessoRepository processos, ClienteRepository clientes) {
        this.processos = processos;
        this.clientes = clientes;
    }

    @Transactional(readOnly = true)
    public PageResponse<ProcessoResponse> listar(String busca, Long clienteId, TipoProcesso tipo, String areaJuridica,
                                                 String tribunal, SituacaoProcesso situacao, int page, int size) {
        var pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
                Sort.by("proximoPrazo").ascending().and(Sort.by("id").ascending()));
        return PageResponse.of(processos.findAll(filtro(busca, clienteId, tipo, areaJuridica, tribunal, situacao), pageable),
                ProcessoResponse::of);
    }

    @Transactional(readOnly = true)
    public ProcessoResumo resumo() {
        long emAndamento = processos.count(filtro(null, null, null, null, null, SituacaoProcesso.EM_ANDAMENTO));
        long suspensos = processos.count(filtro(null, null, null, null, null, SituacaoProcesso.SUSPENSO));
        long encerrados = processos.count(filtro(null, null, null, null, null, SituacaoProcesso.ENCERRADO));
        long urgentes = processos.count(filtro(null, null, null, null, null, SituacaoProcesso.URGENTE));
        long total = processos.count(filtro(null, null, null, null, null, null));
        return new ProcessoResumo(total, emAndamento, suspensos, encerrados, urgentes);
    }

    @Transactional(readOnly = true)
    public ProcessoResponse buscar(Long id) {
        return ProcessoResponse.of(obter(id));
    }

    @Transactional
    public ProcessoResponse criar(ProcessoRequest r) {
        String numero = numeroValido(r.numero());
        if (processos.findByNumero(numero).isPresent()) {
            throw numeroDuplicado();
        }
        Cliente cliente = obterCliente(r.clienteId());
        Processo p = new Processo(cliente, numero, r.tipo(), r.areaJuridica().trim(), r.tribunal().trim(),
                r.advogadoNome().trim(), r.situacao());
        aplicar(p, r, cliente, numero);
        return ProcessoResponse.of(salvar(p));
    }

    @Transactional
    public ProcessoResponse atualizar(Long id, ProcessoRequest r) {
        Processo p = obter(id);
        String numero = numeroValido(r.numero());
        processos.findByNumero(numero).filter(outro -> !outro.getId().equals(id)).ifPresent(outro -> {
            throw numeroDuplicado();
        });
        aplicar(p, r, obterCliente(r.clienteId()), numero);
        return ProcessoResponse.of(salvar(p));
    }

    private Processo obter(Long id) {
        return processos.findById(id).orElseThrow(() -> BusinessException.notFound("Processo nao encontrado"));
    }

    private Cliente obterCliente(Long id) {
        return clientes.findById(id).orElseThrow(() -> BusinessException.notFound("Cliente nao encontrado"));
    }

    private Processo salvar(Processo p) {
        try {
            return processos.saveAndFlush(p);
        } catch (DataIntegrityViolationException e) {
            throw numeroDuplicado();
        }
    }

    private static BusinessException numeroDuplicado() {
        return BusinessException.conflict("Ja existe um processo com este numero");
    }

    private static String numeroValido(String numero) {
        String digitos = NumeroCnj.somenteDigitos(numero);
        if (!NumeroCnj.valido(digitos)) {
            throw BusinessException.badRequest("Numero CNJ invalido");
        }
        return digitos;
    }

    private static void aplicar(Processo p, ProcessoRequest r, Cliente cliente, String numero) {
        p.atualizar(cliente, numero, r.tipo(), r.areaJuridica().trim(), r.tribunal().trim(), texto(r.comarca()),
                texto(r.vara()), r.advogadoNome().trim(), texto(r.advogadoOab()), r.situacao(), r.proximoPrazo(),
                texto(r.proximoPrazoDescricao()), texto(r.observacoes()));
    }

    private static String texto(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }

    /** Filtros opcionais; o tenant e aplicado pelo Hibernate (@TenantId) em qualquer consulta. */
    private static Specification<Processo> filtro(String busca, Long clienteId, TipoProcesso tipo, String areaJuridica,
                                                  String tribunal, SituacaoProcesso situacao) {
        return (root, query, cb) -> {
            Join<Processo, Cliente> cliente;
            if (query != null && query.getResultType() != Long.class && query.getResultType() != long.class) {
                cliente = (Join<Processo, Cliente>) (Object) root.fetch("cliente", JoinType.INNER);
            } else {
                cliente = root.join("cliente", JoinType.INNER);
            }
            List<Predicate> and = new ArrayList<>();
            if (clienteId != null) {
                and.add(cb.equal(cliente.get("id"), clienteId));
            }
            if (tipo != null) {
                and.add(cb.equal(root.get("tipo"), tipo));
            }
            if (areaJuridica != null && !areaJuridica.isBlank()) {
                and.add(cb.equal(root.get("areaJuridica"), areaJuridica.trim()));
            }
            if (tribunal != null && !tribunal.isBlank()) {
                and.add(cb.equal(root.get("tribunal"), tribunal.trim()));
            }
            if (situacao != null) {
                and.add(cb.equal(root.get("situacao"), situacao));
            }
            if (busca != null && !busca.isBlank()) {
                String termo = "%" + escapar(busca.trim().toLowerCase()) + "%";
                List<Predicate> or = new ArrayList<>();
                or.add(cb.like(cb.lower(root.get("numero")), termo, '\\'));
                or.add(cb.like(cb.lower(root.get("advogadoNome")), termo, '\\'));
                or.add(cb.like(cb.lower(cliente.get("nome")), termo, '\\'));
                String digitos = Documento.somenteDigitos(busca);
                if (!digitos.isEmpty()) {
                    or.add(cb.like(root.get("numero"), "%" + digitos + "%"));
                    or.add(cb.like(cliente.get("documento"), "%" + digitos + "%"));
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
