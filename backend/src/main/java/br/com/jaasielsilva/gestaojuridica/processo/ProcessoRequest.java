package br.com.jaasielsilva.gestaojuridica.processo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/** Dados de cadastro/edicao. O numero CNJ pode vir com mascara; o service normaliza. */
public record ProcessoRequest(
        @NotBlank @Size(max = 25) String numero,
        @NotNull Long clienteId,
        @NotNull TipoProcesso tipo,
        @NotBlank @Size(max = 100) String areaJuridica,
        @NotBlank @Size(max = 150) String tribunal,
        @Size(max = 150) String comarca,
        @Size(max = 150) String vara,
        @NotBlank @Size(max = 200) String advogadoNome,
        @Size(max = 30) String advogadoOab,
        @NotNull SituacaoProcesso situacao,
        LocalDate proximoPrazo,
        @Size(max = 200) String proximoPrazoDescricao,
        @Size(max = 2000) String observacoes) {
}
