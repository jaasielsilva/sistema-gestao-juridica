package br.com.jaasielsilva.gestaojuridica.cliente;

import br.com.jaasielsilva.gestaojuridica.shared.BusinessException;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@Service
public class ClienteService {

    static final int MAX_PAGE_SIZE = 100;
    static final int MIN_BUSCA = 3;
    private static final Pattern SOMENTE_DOCUMENTO = Pattern.compile("[\\d.\\-/\\s]+");

    private final ClienteRepository clientes;

    public ClienteService(ClienteRepository clientes) {
        this.clientes = clientes;
    }

    @Transactional(readOnly = true)
    public PageResponse<ClienteResponse> listar(String busca, Boolean ativo, TipoPessoa tipo, String cidade, String uf,
                                                 int page, int size) {
        if (busca != null && !busca.isBlank() && busca.trim().length() < MIN_BUSCA) {
            throw BusinessException.badRequest("Informe ao menos " + MIN_BUSCA + " caracteres para buscar");
        }
        var pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
                Sort.by("nome").ascending().and(Sort.by("id")));
        return PageResponse.of(clientes.findAll(filtro(busca, ativo, tipo, cidade, uf), pageable), ClienteResponse::of);
    }

    @Transactional(readOnly = true)
    public ClienteResumo resumo() {
        long ativos = clientes.count(filtro(null, true, null, null, null));
        long inativos = clientes.count(filtro(null, false, null, null, null));
        return new ClienteResumo(ativos + inativos, ativos, inativos,
                clientes.count(filtro(null, null, TipoPessoa.PF, null, null)),
                clientes.count(filtro(null, null, TipoPessoa.PJ, null, null)));
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
    private static Specification<Cliente> filtro(String busca, Boolean ativo, TipoPessoa tipo, String cidade, String uf) {
        return (root, query, cb) -> {
            List<Predicate> and = new ArrayList<>();
            if (ativo != null) {
                and.add(cb.equal(root.get("ativo"), ativo));
            }
            if (tipo != null) {
                and.add(cb.equal(root.get("tipo"), tipo));
            }
            if (cidade != null && !cidade.isBlank()) {
                // O banco usa collation case-insensitive (CI_AS): sem lower() nas colunas o índice ix_cliente_busca cobre o filtro.
                String termoCidade = "%" + escapar(cidade.trim()) + "%";
                and.add(cb.like(root.get("cidade"), termoCidade, '\\'));
            }
            if (uf != null && !uf.isBlank()) {
                and.add(cb.equal(root.get("uf"), uf.trim().toUpperCase()));
            }
            if (busca != null && !busca.isBlank()) {
                and.add(buscaPorPadrao(root, cb, busca.trim()));
            }
            return cb.and(and.toArray(new Predicate[0]));
        };
    }

    /**
     * Escolhe a coluna pelo formato do termo, sempre sem coringa inicial, para o banco usar seek no indice
     * (CPF/CNPJ completo: igualdade; e-mail e nome: prefixo; trecho de documento: prefixo dos digitos).
     */
    private static Predicate buscaPorPadrao(Root<Cliente> root, CriteriaBuilder cb, String termo) {
        String digitos = Documento.somenteDigitos(termo);
        if (termo.contains("@")) {
            return cb.like(root.get("email"), escapar(termo) + "%", '\\');
        }
        if (!digitos.isEmpty() && SOMENTE_DOCUMENTO.matcher(termo).matches()) {
            if (digitos.length() == 11 || digitos.length() == 14) {
                return cb.equal(root.get("documento"), digitos);
            }
            return cb.like(root.get("documento"), digitos + "%");
        }
        return cb.like(root.get("nome"), escapar(termo) + "%", '\\');
    }

    private static String escapar(String s) {
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
