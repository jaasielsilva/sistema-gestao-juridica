package br.com.jaasielsilva.gestaojuridica.integracao.escavador;

import br.com.jaasielsilva.gestaojuridica.tenant.TenantScopedRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface MovimentacaoRepository extends TenantScopedRepository<Movimentacao> {

    boolean existsByProcessoIdAndHashDedup(Long processoId, String hashDedup);

    Page<Movimentacao> findByProcessoIdOrderByDataMovimentoDesc(Long processoId, Pageable pageable);
}
