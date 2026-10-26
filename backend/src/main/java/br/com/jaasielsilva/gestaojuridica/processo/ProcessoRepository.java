package br.com.jaasielsilva.gestaojuridica.processo;

import br.com.jaasielsilva.gestaojuridica.tenant.TenantScopedRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ProcessoRepository extends TenantScopedRepository<Processo>, JpaSpecificationExecutor<Processo> {

    /** Filtrado pelo tenant do token (@TenantId): so enxerga numeros do proprio tenant. */
    Optional<Processo> findByNumero(String numero);

    /** Contagem por situacao numa unica consulta (o tenant e filtrado pelo @TenantId). Linhas: {situacao, quantidade}. */
    @Query("select p.situacao, count(p) from Processo p group by p.situacao")
    List<Object[]> contarPorSituacao();
}
