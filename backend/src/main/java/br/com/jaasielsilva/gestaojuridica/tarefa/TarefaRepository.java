package br.com.jaasielsilva.gestaojuridica.tarefa;

import br.com.jaasielsilva.gestaojuridica.tenant.TenantScopedRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TarefaRepository extends TenantScopedRepository<Tarefa>, JpaSpecificationExecutor<Tarefa> {

    List<Tarefa> findByStatusOrderByOrdemAscIdAsc(StatusTarefa status);

    /** Maior ordem da coluna (-1 se vazia): a tarefa nova entra logo abaixo. O tenant e filtrado pelo @TenantId. */
    @Query("select coalesce(max(t.ordem), -1) from Tarefa t where t.status = :status")
    int maiorOrdem(@Param("status") StatusTarefa status);
}
