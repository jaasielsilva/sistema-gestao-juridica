package br.com.jaasielsilva.gestaojuridica.cliente;

import br.com.jaasielsilva.gestaojuridica.tenant.TenantScopedRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ClienteRepository extends TenantScopedRepository<Cliente>, JpaSpecificationExecutor<Cliente> {

    /** Filtrado pelo tenant do token (@TenantId): so enxerga documentos do proprio tenant. */
    Optional<Cliente> findByDocumento(String documento);

    /** Contagem por situacao e tipo numa unica consulta (o tenant e filtrado pelo @TenantId). Linhas: {ativo, tipo, quantidade}. */
    @Query("select c.ativo, c.tipo, count(c) from Cliente c group by c.ativo, c.tipo")
    List<Object[]> contarPorAtivoETipo();
}
