package com.example.app.tenant;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.NoRepositoryBean;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * Repositorio base das entidades isoladas por tenant. Redeclara findById como consulta JPQL, que (diferente de
 * EntityManager.find) recebe o filtro de tenant do Hibernate: o ID de outro tenant simplesmente nao e encontrado.
 */
@NoRepositoryBean
public interface TenantScopedRepository<T extends TenantScopedEntity> extends JpaRepository<T, Long> {

    @Override
    @Query("select e from #{#entityName} e where e.id = :id")
    Optional<T> findById(@Param("id") Long id);
}
