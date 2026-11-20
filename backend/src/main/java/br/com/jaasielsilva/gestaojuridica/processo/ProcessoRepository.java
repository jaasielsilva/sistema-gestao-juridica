package br.com.jaasielsilva.gestaojuridica.processo;

import br.com.jaasielsilva.gestaojuridica.tenant.TenantScopedRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ProcessoRepository extends TenantScopedRepository<Processo>, JpaSpecificationExecutor<Processo> {

    /** Filtrado pelo tenant do token (@TenantId): so enxerga numeros do proprio tenant. */
    Optional<Processo> findByNumero(String numero);

    /** Contagem por situacao numa unica consulta (o tenant e filtrado pelo @TenantId). Linhas: {situacao, quantidade}. */
    @Query("select p.situacao, count(p) from Processo p group by p.situacao")
    List<Object[]> contarPorSituacao();

    /** Processos do radar por etapa. Linhas: {etapa, quantidade}. */
    @Query("select p.etapa, count(p) from Processo p where p.situacao in :situacoes group by p.etapa")
    List<Object[]> contarPorEtapa(@Param("situacoes") Collection<SituacaoProcesso> situacoes);

    /** Quantos processos estao monitorados em cada frequencia — base do calculo de gasto mensal com o Escavador. */
    @Query("select p.frequenciaMonitoramento, count(p) from Processo p " +
           "where p.frequenciaMonitoramento is not null group by p.frequenciaMonitoramento")
    List<Object[]> contarPorFrequenciaMonitoramento();
}
