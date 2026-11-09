package br.com.jaasielsilva.gestaojuridica.integracao.escavador;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;

/** Token e callback secret da PROPRIA conta Escavador do escritorio — nunca armazenados em texto puro. */
public record EscavadorConfiguracaoRequest(
        @NotBlank String token,
        @NotBlank String callbackSecret,
        @PositiveOrZero long saldoLimiteCentavos) {
}
