package br.com.jaasielsilva.gestaojuridica.cliente;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Cadastro de clientes. Qualquer usuario autenticado lista, cria e edita; inativar/reativar e so do ADMIN.
 * Nenhum metodo menciona tenant: o isolamento e automatico (ver TenantScopedEntity).
 */
@RestController
@RequestMapping("/api/v1/clientes")
public class ClienteController {

    private final ClienteService service;

    public ClienteController(ClienteService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<ClienteResponse> listar(@RequestParam(required = false) String busca,
                                                @RequestParam(required = false) Boolean ativo,
                                                @RequestParam(required = false) TipoPessoa tipo,
                                                @RequestParam(required = false) String cidade,
                                                @RequestParam(required = false) String uf,
                                                @RequestParam(defaultValue = "0") int page,
                                                @RequestParam(defaultValue = "10") int size) {
        return service.listar(busca, ativo, tipo, cidade, uf, page, size);
    }

    @GetMapping("/resumo")
    public ClienteResumo resumo() {
        return service.resumo();
    }

    @GetMapping("/{id}")
    public ClienteResponse buscar(@PathVariable Long id) {
        return service.buscar(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ClienteResponse criar(@Valid @RequestBody ClienteRequest request) {
        return service.criar(request);
    }

    @PutMapping("/{id}")
    public ClienteResponse atualizar(@PathVariable Long id, @Valid @RequestBody ClienteRequest request) {
        return service.atualizar(id, request);
    }

    @PostMapping("/{id}/inativar")
    @PreAuthorize("hasRole('ADMIN')")
    public ClienteResponse inativar(@PathVariable Long id) {
        return service.alterarStatus(id, false);
    }

    @PostMapping("/{id}/reativar")
    @PreAuthorize("hasRole('ADMIN')")
    public ClienteResponse reativar(@PathVariable Long id) {
        return service.alterarStatus(id, true);
    }
}
