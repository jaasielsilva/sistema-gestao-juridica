package br.com.jaasielsilva.gestaojuridica.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    /** Usuario ativo de um tenant especifico (Usuario nao e @TenantId: o tenant e sempre passado explicitamente). */
    Optional<Usuario> findByIdAndTenantIdAndAtivoTrue(Long id, Long tenantId);

    List<Usuario> findByTenantIdAndAtivoTrueOrderByNomeAsc(Long tenantId);

    Optional<Usuario> findByEmail(String email);

    boolean existsByEmail(String email);
}
