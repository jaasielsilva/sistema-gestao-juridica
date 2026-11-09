package br.com.jaasielsilva.gestaojuridica.integracao.escavador;

import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * Resolve o tenant a partir do slug de callback ANTES de existir qualquer contexto de tenant —
 * o callback do Escavador nao tem JWT, entao nao ha tenant atual para o Hibernate filtrar por
 * (ver TenantHibernateConfig). Por isso esta consulta e nativa, fora do ORM: nao passa pelo filtro
 * de @TenantId (que so se aplica a entidades carregadas via JPA), e so expoe as 3 colunas minimas
 * necessarias para validar o chamador e decidir qual tenant usar a partir daqui.
 */
@Component
public class EscavadorCallbackLookup {

    @PersistenceContext
    private EntityManager em;

    public Optional<CallbackAlvo> buscarPorSlug(UUID slug) {
        try {
            Object[] row = (Object[]) em.createNativeQuery(
                    "select tenant_id, callback_secret_cifrado, habilitado " +
                    "from dbo.escavador_configuracao where callback_slug = :slug")
                    .setParameter("slug", slug)
                    .getSingleResult();
            Long tenantId = ((Number) row[0]).longValue();
            String secret = (String) row[1];
            boolean habilitado = ((Boolean) row[2]);
            return Optional.of(new CallbackAlvo(tenantId, secret, habilitado));
        } catch (NoResultException ex) {
            return Optional.empty();
        }
    }
}
