package br.com.jaasielsilva.gestaojuridica.nota;

import br.com.jaasielsilva.gestaojuridica.security.AuthPrincipal;
import br.com.jaasielsilva.gestaojuridica.shared.BusinessException;
import br.com.jaasielsilva.gestaojuridica.user.Papel;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

/**
 * MODULO DE EXEMPLO. Repare que nenhum metodo menciona tenant: o isolamento e automatico (@TenantId na entidade).
 * Buscar o id de uma nota de OUTRO tenant resulta em 404, exatamente como se ela nao existisse.
 */
@RestController
@RequestMapping("/api/v1/notas")
public class NotaController {

    public record NotaRequest(@NotBlank @Size(max = 200) String titulo) {
    }

    public record NotaResponse(Long id, String titulo, Long criadoPor, Instant criadoEm) {
        static NotaResponse of(Nota n) {
            return new NotaResponse(n.getId(), n.getTitulo(), n.getCriadoPor(), n.getCriadoEm());
        }
    }

    private final NotaRepository notas;

    public NotaController(NotaRepository notas) {
        this.notas = notas;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<NotaResponse> listar(@AuthenticationPrincipal AuthPrincipal user) {
        List<Nota> visiveis = user.papel() == Papel.ADMIN
                ? notas.findAllByOrderByCriadoEmDescIdDesc()
                : notas.findAllByCriadoPorOrderByCriadoEmDescIdDesc(user.userId());
        return visiveis.stream().map(NotaResponse::of).toList();
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public NotaResponse buscar(@PathVariable Long id, @AuthenticationPrincipal AuthPrincipal user) {
        Nota nota = notas.findById(id).orElseThrow(() -> BusinessException.notFound("Nota nao encontrada"));
        exigirPropriedadeOuAdmin(nota, user);
        return NotaResponse.of(nota);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public NotaResponse criar(@AuthenticationPrincipal AuthPrincipal user, @Valid @RequestBody NotaRequest request) {
        return NotaResponse.of(notas.save(new Nota(request.titulo().trim(), user.userId())));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void apagar(@PathVariable Long id, @AuthenticationPrincipal AuthPrincipal user) {
        Nota nota = notas.findById(id).orElseThrow(() -> BusinessException.notFound("Nota nao encontrada"));
        exigirPropriedadeOuAdmin(nota, user);
        notas.delete(nota);
    }

    private static void exigirPropriedadeOuAdmin(Nota nota, AuthPrincipal user) {
        if (!nota.getCriadoPor().equals(user.userId()) && user.papel() != Papel.ADMIN) {
            throw BusinessException.forbidden("Voce nao pode acessar esta nota");
        }
    }
}
