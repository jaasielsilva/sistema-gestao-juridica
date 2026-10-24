package br.com.jaasielsilva.gestaojuridica.processo;

import br.com.jaasielsilva.gestaojuridica.tenant.TenantScopedRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface ProcessoRepository extends TenantScopedRepository<Processo>, JpaSpecificationExecutor<Processo> {

    /** Filtrado pelo tenant do token (@TenantId): so enxerga numeros do proprio tenant. */
    Optional<Processo> findByNumero(String numero);
}
