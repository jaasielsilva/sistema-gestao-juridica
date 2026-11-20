package br.com.jaasielsilva.gestaojuridica.processo;

import jakarta.validation.constraints.NotNull;

public record MudarEtapaRequest(@NotNull EtapaProcesso etapa) {
}
