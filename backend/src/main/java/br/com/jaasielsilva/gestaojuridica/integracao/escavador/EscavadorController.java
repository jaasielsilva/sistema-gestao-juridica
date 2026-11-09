package br.com.jaasielsilva.gestaojuridica.integracao.escavador;

import br.com.jaasielsilva.gestaojuridica.security.AuthPrincipal;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
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

/** Configuracao da integracao e ativacao de monitoramento por processo. So o admin do escritorio configura a chave. */
@RestController
@RequestMapping("/api/v1/integracoes/escavador")
public class EscavadorController {

    private final EscavadorService service;

    public EscavadorController(EscavadorService service) {
        this.service = service;
    }

    @GetMapping("/configuracao")
    @PreAuthorize("hasRole('ADMIN')")
    public EscavadorConfiguracaoResponse obterConfiguracao() {
        return service.obterConfiguracao();
    }

    @PutMapping("/configuracao")
    @PreAuthorize("hasRole('ADMIN')")
    public EscavadorConfiguracaoResponse salvarConfiguracao(@Valid @RequestBody EscavadorConfiguracaoRequest request,
                                                             @AuthenticationPrincipal AuthPrincipal quem) {
        return service.salvarConfiguracao(request, quem);
    }

    @PostMapping("/processos/{processoId}/monitoramento")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void ativarMonitoramento(@PathVariable Long processoId, @Valid @RequestBody MonitoramentoRequest request,
                                    @AuthenticationPrincipal AuthPrincipal quem) {
        service.ativarMonitoramento(processoId, request.frequencia(), quem);
    }

    @DeleteMapping("/processos/{processoId}/monitoramento")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void desativarMonitoramento(@PathVariable Long processoId, @AuthenticationPrincipal AuthPrincipal quem) {
        service.desativarMonitoramento(processoId, quem);
    }

    @GetMapping("/processos/{processoId}/movimentacoes")
    public Page<MovimentacaoResponse> listarMovimentacoes(@PathVariable Long processoId,
                                                           @RequestParam(defaultValue = "0") int page,
                                                           @RequestParam(defaultValue = "20") int size) {
        return service.listarMovimentacoes(processoId, PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 50)));
    }
}
