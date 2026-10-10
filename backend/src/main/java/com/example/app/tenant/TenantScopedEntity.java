package com.example.app.tenant;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PostLoad;
import org.hibernate.annotations.TenantId;

import java.util.Objects;

/**
 * Base de TODA entidade de negocio isolada por tenant.
 *
 * Camada 1: @TenantId faz o Hibernate filtrar consultas (JPQL/derived queries) e preencher o tenant_id no INSERT.
 * Camada 2: @PostLoad e a rede de seguranca. O Hibernate NAO aplica o filtro em EntityManager.find (usado por
 * findById padrao, deleteById...), entao, se uma linha de outro tenant for carregada por esse caminho, a leitura e
 * abortada em vez de vazar o dado. Use TenantScopedRepository, que ja redeclara findById com consulta filtrada.
 */
@MappedSuperclass
public abstract class TenantScopedEntity {

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private Long tenantId;

    @PostLoad
    void garantirMesmoTenant() {
        if (!Objects.equals(tenantId, TenantContext.currentOrNone())) {
            throw new TenantViolationException(getClass().getSimpleName());
        }
    }

    public Long getTenantId() {
        return tenantId;
    }
}
