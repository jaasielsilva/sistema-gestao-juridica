package br.com.jaasielsilva.gestaojuridica.prazo;

import br.com.jaasielsilva.gestaojuridica.processo.PageResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Prazos processuais. Qualquer usuario autenticado cadastra, edita e baixa; excluir e so do ADMIN. */
@RestController
@RequestMapping("/api/v1/prazos")
public class PrazoController {

    private final PrazoService service;

    public PrazoController(PrazoService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<PrazoResponse> listar(@RequestParam(required = false) SituacaoPrazo situacao,
                                              @RequestParam(required = false) Integer dias,
                                              @RequestParam(required = false) CategoriaPrazo categoria,
                                              @RequestParam(required = false) TipoPrazo tipo,
                                              @RequestParam(required = false) Long responsavelId,
                                              @RequestParam(required = false) Long processoId,
                                              @RequestParam(required = false) String busca,
                                              @RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "10") int size) {
        return service.listar(situacao, dias, categoria, tipo, responsavelId, processoId, busca, page, size);
    }

    @GetMapping("/resumo")
    public PrazoResumo resumo() {
        return service.resumo();
    }

    @GetMapping("/tipos")
    public List<TipoPrazoResponse> tipos() {
        return service.tipos();
    }

    @GetMapping("/{id}")
    public PrazoResponse buscar(@PathVariable Long id) {
        return service.buscar(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PrazoResponse criar(@Valid @RequestBody PrazoRequest request) {
        return service.criar(request);
    }

    @PutMapping("/{id}")
    public PrazoResponse atualizar(@PathVariable Long id, @Valid @RequestBody PrazoRequest request) {
        return service.atualizar(id, request);
    }

    @PostMapping("/{id}/cumprir")
    public PrazoResponse cumprir(@PathVariable Long id) {
        return service.cumprir(id);
    }

    @PostMapping("/{id}/reabrir")
    public PrazoResponse reabrir(@PathVariable Long id) {
        return service.reabrir(id);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void excluir(@PathVariable Long id) {
        service.excluir(id);
    }
}
