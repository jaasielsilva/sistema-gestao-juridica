package br.com.jaasielsilva.gestaojuridica.user;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/usuarios")
public class ResponsavelController {

    private final ResponsavelService service;

    public ResponsavelController(ResponsavelService service) {
        this.service = service;
    }

    /** Colegas do proprio tenant, para escolher o responsavel de um prazo ou tarefa. */
    @GetMapping("/responsaveis")
    public List<ResponsavelResponse> responsaveis() {
        return service.listar();
    }
}
