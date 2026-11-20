package br.com.jaasielsilva.gestaojuridica.tarefa;

import br.com.jaasielsilva.gestaojuridica.security.AuthPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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

/** Quadro Kanban de tarefas. Qualquer usuario autenticado cria, edita e move; apagar e de quem criou ou do ADMIN. */
@RestController
@RequestMapping("/api/v1/tarefas")
public class TarefaController {

    private final TarefaService service;

    public TarefaController(TarefaService service) {
        this.service = service;
    }

    @GetMapping
    public List<TarefaResponse> quadro(@RequestParam(required = false) Long responsavelId,
                                       @RequestParam(required = false) Long processoId,
                                       @RequestParam(required = false) String busca) {
        return service.quadro(responsavelId, processoId, busca);
    }

    @GetMapping("/{id}")
    public TarefaResponse buscar(@PathVariable Long id) {
        return service.buscar(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TarefaResponse criar(@AuthenticationPrincipal AuthPrincipal usuario, @Valid @RequestBody TarefaRequest request) {
        return service.criar(request, usuario);
    }

    @PutMapping("/{id}")
    public TarefaResponse atualizar(@PathVariable Long id, @Valid @RequestBody TarefaRequest request) {
        return service.atualizar(id, request);
    }

    @PutMapping("/{id}/mover")
    public TarefaResponse mover(@PathVariable Long id, @Valid @RequestBody MoverTarefaRequest request) {
        return service.mover(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void excluir(@PathVariable Long id, @AuthenticationPrincipal AuthPrincipal usuario) {
        service.excluir(id, usuario);
    }
}
