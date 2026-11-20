package br.com.jaasielsilva.gestaojuridica.prazo;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/** Cadastro/edicao de prazo. Na edicao o processo nao muda (processoId e ignorado). */
public record PrazoRequest(
        @NotNull Long processoId,
        @NotNull TipoPrazo tipo,
        @Size(max = 300) String descricao,
        @NotNull LocalDate dataVencimento,
        Long responsavelId) {
}
