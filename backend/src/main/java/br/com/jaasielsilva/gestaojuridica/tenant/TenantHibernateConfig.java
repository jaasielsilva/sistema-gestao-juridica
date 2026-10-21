package br.com.jaasielsilva.gestaojuridica.tenant;

import org.hibernate.cfg.AvailableSettings;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Multi-tenancy por coluna discriminadora. Toda entidade com @TenantId tem o filtro
 * "tenant_id = tenant atual" aplicado automaticamente pelo Hibernate em consultas, find e deletes,
 * e o tenant_id e preenchido sozinho no INSERT. O desenvolvedor nao precisa lembrar de filtrar.
 */
@Configuration
public class TenantHibernateConfig {

    @Bean
    CurrentTenantIdentifierResolver<Long> tenantIdentifierResolver() {
        return new CurrentTenantIdentifierResolver<>() {
            @Override
            public Long resolveCurrentTenantIdentifier() {
                return TenantContext.currentOrNone();
            }

            @Override
            public boolean validateExistingCurrentSessions() {
                return false;
            }
        };
    }

    @Bean
    HibernatePropertiesCustomizer tenantResolverCustomizer(CurrentTenantIdentifierResolver<Long> resolver) {
        return props -> props.put(AvailableSettings.MULTI_TENANT_IDENTIFIER_RESOLVER, resolver);
    }
}
