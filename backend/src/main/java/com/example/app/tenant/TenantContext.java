package com.example.app.tenant;

/**
 * Tenant (empresa) da requisicao atual. E preenchido SOMENTE pelo filtro JWT, a partir do token assinado:
 * nunca de parametro, header ou corpo enviado pelo cliente.
 */
public final class TenantContext {

    /** Tenant padrao para auto-cadastro (linha semeada em 002_create_tenant.sql). */
    public static final long DEFAULT_TENANT_ID = 1L;

    /** Sentinela "sem tenant": nenhuma linha de negocio tem este id, entao consultas retornam vazio (falha fechada). */
    public static final long NONE = -1L;

    private static final ThreadLocal<Long> CURRENT = new ThreadLocal<>();

    private TenantContext() {
    }

    public static void set(Long tenantId) {
        CURRENT.set(tenantId);
    }

    public static long currentOrNone() {
        Long id = CURRENT.get();
        return id == null ? NONE : id;
    }

    public static void clear() {
        CURRENT.remove();
    }
}
