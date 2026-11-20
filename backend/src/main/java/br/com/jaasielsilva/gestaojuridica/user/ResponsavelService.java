package br.com.jaasielsilva.gestaojuridica.user;

import br.com.jaasielsilva.gestaojuridica.shared.BusinessException;
import br.com.jaasielsilva.gestaojuridica.tenant.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Usuarios do proprio tenant que podem ser designados responsaveis por prazos e tarefas. */
@Service
public class ResponsavelService {

    private final UsuarioRepository usuarios;

    public ResponsavelService(UsuarioRepository usuarios) {
        this.usuarios = usuarios;
    }

    @Transactional(readOnly = true)
    public List<ResponsavelResponse> listar() {
        return usuarios.findByTenantIdAndAtivoTrueOrderByNomeAsc(TenantContext.currentOrNone()).stream()
                .map(u -> new ResponsavelResponse(u.getId(), u.getNome()))
                .toList();
    }

    /** Devolve o usuario ativo do tenant atual; id de outro tenant (ou inativo) e tratado como invalido. Nulo -> nulo. */
    @Transactional(readOnly = true)
    public Usuario obterOuNulo(Long id) {
        if (id == null) {
            return null;
        }
        return usuarios.findByIdAndTenantIdAndAtivoTrue(id, TenantContext.currentOrNone())
                .orElseThrow(() -> BusinessException.badRequest("Responsavel invalido"));
    }
}
