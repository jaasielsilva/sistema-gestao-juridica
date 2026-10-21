package br.com.jaasielsilva.gestaojuridica.nota;

import br.com.jaasielsilva.gestaojuridica.tenant.TenantScopedRepository;

import java.util.List;

public interface NotaRepository extends TenantScopedRepository<Nota> {

    List<Nota> findAllByOrderByCriadoEmDescIdDesc();

    List<Nota> findAllByCriadoPorOrderByCriadoEmDescIdDesc(Long criadoPor);
}
