package br.com.jaasielsilva.gestaojuridica.cliente;

import br.com.jaasielsilva.gestaojuridica.shared.BusinessException;
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
public class ClienteService {

    static final int MAX_PAGE_SIZE = 100;

    private final ClienteRepository clientes;

    public ClienteService(ClienteRepository clientes) {
        this.clientes = clientes;
    }

    @Transactional(readOnly = true)
    public PageResponse<ClienteResponse> listar(String busca, Boolean ativo, TipoPessoa tipo, int page, int size) {
        var pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
                Sort.by("nome").ascending().and(Sort.by("id")));
        return PageResponse.of(clientes.findAll(filtro(busca, ativo, tipo), pageable), ClienteResponse::of);
    }

    @Transactional(readOnly = true)
    public ClienteResumo resumo() {
        long ativos = clientes.count(filtro(null, true, null));
        long inativos = clientes.count(filtro(null, false, null));
        return new ClienteResumo(ativos + inativos, ativos, inativos,
                clientes.count(filtro(null, null, TipoPessoa.PF)), clientes.count(filtro(null, null, TipoPessoa.PJ)));
    }

    @Transactional(readOnly = true)
    public ClienteResponse buscar(Long id) {
        return ClienteResponse.of(obter(id));
    }

    @Transactional
    public ClienteResponse criar(ClienteRequest r) {
        String documento = documentoValido(r);
        if (clientes.findByDocumento(documento).isPresent()) {
            throw documentoDuplicado();
        }
        Cliente c = new Cliente(r.tipo(), r.nome().trim(), documento);
        aplicar(c, r, documento);
        return ClienteResponse.of(salvar(c));
    }

    @Transactional
    public ClienteResponse atualizar(Long id, ClienteRequest r) {
        Cliente c = obter(id);
        String documento = documentoValido(r);
        clientes.findByDocumento(documento).filter(outro -> !outro.getId().equals(id)).ifPresent(outro -> {
            throw documentoDuplicado();
        });
        aplicar(c, r, documento);
        return ClienteResponse.of(salvar(c));
    }

    @Transactional
    public ClienteResponse alterarStatus(Long id, boolean ativo) {
        Cliente c = obter(id);
        c.definirAtivo(ativo);
        return ClienteResponse.of(clientes.save(c));
    }

    private Cliente obter(Long id) {
        return clientes.findById(id).orElseThrow(() -> BusinessException.notFound("Cliente nao encontrado"));
    }

    private Cliente salvar(Cliente c) {
        try {
            return clientes.saveAndFlush(c);
        } catch (DataIntegrityViolationException e) {
            // Corrida entre duas requisicoes com o mesmo documento: o indice unico tem a palavra final.
            throw documentoDuplicado();
        }
    }

    private static BusinessException documentoDuplicado() {
        return BusinessException.conflict("Ja existe um cliente com este CPF/CNPJ");
    }

    private static String documentoValido(ClienteRequest r) {
        String digitos = Documento.somenteDigitos(r.documento());
        if (!Documento.valido(r.tipo(), digitos)) {
            throw BusinessException.badRequest(r.tipo() == TipoPessoa.PF ? "CPF invalido" : "CNPJ invalido");
        }
        return digitos;
    }

    private static void aplicar(Cliente c, ClienteRequest r, String documento) {
        String cep = Documento.somenteDigitos(r.cep());
        if (!cep.isEmpty() && cep.length() != 8) {
            throw BusinessException.badRequest("CEP invalido");
        }
        String telefone = Documento.somenteDigitos(r.telefone());
        if (!telefone.isEmpty() && (telefone.length() < 10 || telefone.length() > 11)) {
            throw BusinessException.badRequest("Telefone invalido");
        }
        c.atualizar(r.tipo(), r.nome().trim(), documento, texto(r.email()), vazioParaNulo(telefone),
                vazioParaNulo(cep), texto(r.logradouro()), texto(r.numero()), texto(r.complemento()),
                texto(r.bairro()), texto(r.cidade()), r.uf() == null || r.uf().isBlank() ? null : r.uf().toUpperCase(),
                texto(r.observacoes()));
    }

    private static String texto(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }

    private static String vazioParaNulo(String v) {
        return v.isEmpty() ? null : v;
    }

    /** Filtros opcionais; o tenant e aplicado pelo Hibernate (@TenantId) em qualquer consulta. */
    private static Specification<Cliente> filtro(String busca, Boolean ativo, TipoPessoa tipo) {
        return (root, query, cb) -> {
            List<Predicate> and = new ArrayList<>();
            if (ativo != null) {
                and.add(cb.equal(root.get("ativo"), ativo));
            }
            if (tipo != null) {
                and.add(cb.equal(root.get("tipo"), tipo));
            }
            if (busca != null && !busca.isBlank()) {
                String termo = "%" + escapar(busca.trim().toLowerCase()) + "%";
                List<Predicate> or = new ArrayList<>();
                or.add(cb.like(cb.lower(root.get("nome")), termo, '\\'));
                or.add(cb.like(cb.lower(root.get("email")), termo, '\\'));
                String digitos = Documento.somenteDigitos(busca);
                if (!digitos.isEmpty()) {
                    or.add(cb.like(root.get("documento"), "%" + digitos + "%"));
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
