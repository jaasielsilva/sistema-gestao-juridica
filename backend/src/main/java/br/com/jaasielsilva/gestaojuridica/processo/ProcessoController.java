package br.com.jaasielsilva.gestaojuridica.processo;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
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
 * Cadastro de processos. Qualquer usuario autenticado lista, cria e edita.
 * Nenhum metodo menciona tenant: o isolamento e automatico (ver TenantScopedEntity).
 */
@RestController
@RequestMapping("/api/v1/processos")
public class ProcessoController {

    private final ProcessoService service;

    public ProcessoController(ProcessoService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<ProcessoResponse> listar(@RequestParam(required = false) String busca,
                                                 @RequestParam(required = false) Long clienteId,
                                                 @RequestParam(required = false) TipoProcesso tipo,
                                                 @RequestParam(required = false) String areaJuridica,
                                                 @RequestParam(required = false) String tribunal,
                                                 @RequestParam(required = false) SituacaoProcesso situacao,
                                                 @RequestParam(defaultValue = "0") int page,
                                                 @RequestParam(defaultValue = "10") int size) {
        return service.listar(busca, clienteId, tipo, areaJuridica, tribunal, situacao, page, size);
    }

    @GetMapping("/resumo")
    public ProcessoResumo resumo() {
        return service.resumo();
    }

    @GetMapping("/{id}")
    public ProcessoResponse buscar(@PathVariable Long id) {
        return service.buscar(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProcessoResponse criar(@Valid @RequestBody ProcessoRequest request) {
        return service.criar(request);
    }

    @PutMapping("/{id}")
    public ProcessoResponse atualizar(@PathVariable Long id, @Valid @RequestBody ProcessoRequest request) {
        return service.atualizar(id, request);
    }
}
