package br.com.jaasielsilva.gestaojuridica.audit;

import br.com.jaasielsilva.gestaojuridica.security.AuthPrincipal;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

/** Consulta da trilha de auditoria. Restrito a ADMIN (SecurityConfig) e ao tenant do proprio administrador. */
@RestController
@RequestMapping("/api/v1/admin/auditoria")
public class AdminAuditController {

    private static final int MAX_PAGE_SIZE = 100;

    private final EventoAuditoriaRepository repo;

    public AdminAuditController(EventoAuditoriaRepository repo) {
        this.repo = repo;
    }

    public record EventoResponse(Long id, Instant ocorridoEm, TipoEvento tipo, Resultado resultado, Long usuarioId,
                                 String emailInformado, String ip, String correlationId, String detalhe) {
    }

    public record PageResponse(List<EventoResponse> itens, int pagina, int tamanho, long total) {
    }

    @GetMapping
    public PageResponse listar(@AuthenticationPrincipal AuthPrincipal admin,
                               @RequestParam(defaultValue = "0") int pagina,
                               @RequestParam(defaultValue = "50") int tamanho) {
        var pageable = PageRequest.of(Math.max(0, pagina), Math.min(Math.max(1, tamanho), MAX_PAGE_SIZE),
                Sort.by(Sort.Direction.DESC, "ocorridoEm", "id"));
        // O filtro por tenant vem do token do administrador, nunca de parametro da requisicao.
        var page = repo.findByTenantId(admin.tenantId(), pageable);
        return new PageResponse(
                page.getContent().stream().map(e -> new EventoResponse(e.getId(), e.getOcorridoEm(), e.getTipo(),
                        e.getResultado(), e.getUsuarioId(), e.getEmailInformado(), e.getIp(), e.getCorrelationId(),
                        e.getDetalhe())).toList(),
                page.getNumber(), page.getSize(), page.getTotalElements());
    }
}
