package com.example.app.nota;

import com.example.app.tenant.TenantScopedRepository;

import java.util.List;

public interface NotaRepository extends TenantScopedRepository<Nota> {

    List<Nota> findAllByOrderByCriadoEmDescIdDesc();

    List<Nota> findAllByCriadoPorOrderByCriadoEmDescIdDesc(Long criadoPor);
}
