package br.com.jaasielsilva.gestaojuridica.integracao.escavador;

import br.com.jaasielsilva.gestaojuridica.tenant.TenantScopedRepository;

/** Uma linha por tenant (ux_escavador_configuracao_tenant); o filtro de tenant do Hibernate basta para achar a certa. */
public interface EscavadorConfiguracaoRepository extends TenantScopedRepository<EscavadorConfiguracao> {
}
