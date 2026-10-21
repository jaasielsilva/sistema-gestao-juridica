package br.com.jaasielsilva.gestaojuridica.auth;

import br.com.jaasielsilva.gestaojuridica.auth.AuthDtos.RegisterRequest;
import br.com.jaasielsilva.gestaojuridica.tenant.Tenant;
import br.com.jaasielsilva.gestaojuridica.tenant.TenantRepository;
import br.com.jaasielsilva.gestaojuridica.user.Papel;
import br.com.jaasielsilva.gestaojuridica.user.Usuario;
import br.com.jaasielsilva.gestaojuridica.user.UsuarioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Creates the workspace and its first owner atomically. */
@Service
public class RegistrationWriter {

    private final TenantRepository tenants;
    private final UsuarioRepository users;

    public RegistrationWriter(TenantRepository tenants, UsuarioRepository users) {
        this.tenants = tenants;
        this.users = users;
    }

    @Transactional
    public Usuario create(RegisterRequest request, String normalizedEmail, String passwordHash) {
        long tenantId = tenants.nextId();
        tenants.saveAndFlush(new Tenant(tenantId, request.organizacao().trim()));
        // The server grants owner access only within this newly created tenant; the client cannot select a role.
        return users.saveAndFlush(new Usuario(tenantId, request.nome().trim(), normalizedEmail,
                passwordHash, Papel.ADMIN));
    }
}
