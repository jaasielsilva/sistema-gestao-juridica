package br.com.jaasielsilva.gestaojuridica.processo;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Radar de processos ativos (somente leitura). A etapa muda por PUT /api/v1/processos/{id}/etapa. */
@RestController
@RequestMapping("/api/v1/radar")
public class RadarController {

    private final RadarService service;

    public RadarController(RadarService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<RadarItem> listar(@RequestParam(required = false) RadarFaixa faixa,
                                          @RequestParam(required = false) EtapaProcesso etapa,
                                          @RequestParam(required = false) String busca,
                                          @RequestParam(defaultValue = "0") int page,
                                          @RequestParam(defaultValue = "10") int size) {
        return service.listar(faixa, etapa, busca, page, size);
    }

    @GetMapping("/resumo")
    public RadarResumo resumo() {
        return service.resumo();
    }
}
