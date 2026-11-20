package br.com.jaasielsilva.gestaojuridica.prazo;

import br.com.jaasielsilva.gestaojuridica.tenant.TenantScopedRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

public interface PrazoRepository extends TenantScopedRepository<Prazo>, JpaSpecificationExecutor<Prazo> {

    long countByStatusAndDataVencimento(StatusPrazo status, LocalDate data);

    long countByStatusAndDataVencimentoGreaterThan(StatusPrazo status, LocalDate data);

    long countByStatusAndDataVencimentoLessThan(StatusPrazo status, LocalDate data);

    long countByStatusAndCumpridoEmGreaterThanEqual(StatusPrazo status, Instant desde);

    /** Prazo aberto mais proximo de um processo: alimenta processo.proximo_prazo. */
    Optional<Prazo> findFirstByProcessoIdAndStatusOrderByDataVencimentoAscIdAsc(Long processoId, StatusPrazo status);
}
