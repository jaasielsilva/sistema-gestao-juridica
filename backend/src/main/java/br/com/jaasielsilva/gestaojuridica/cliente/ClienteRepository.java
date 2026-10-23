package br.com.jaasielsilva.gestaojuridica.cliente;

import br.com.jaasielsilva.gestaojuridica.tenant.TenantScopedRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface ClienteRepository extends TenantScopedRepository<Cliente>, JpaSpecificationExecutor<Cliente> {

    /** Filtrado pelo tenant do token (@TenantId): so enxerga documentos do proprio tenant. */
    Optional<Cliente> findByDocumento(String documento);
}
