package br.com.jaasielsilva.gestaojuridica.tenant;

/** Tentativa de carregar dado de outro tenant. Nao deve ocorrer em codigo correto; e tratada como "nao encontrado". */
public class TenantViolationException extends RuntimeException {

    public TenantViolationException(String entity) {
        super("Acesso entre tenants bloqueado ao carregar " + entity);
    }
}
