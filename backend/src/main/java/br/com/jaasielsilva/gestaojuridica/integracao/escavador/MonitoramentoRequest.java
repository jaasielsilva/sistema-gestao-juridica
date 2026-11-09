package br.com.jaasielsilva.gestaojuridica.integracao.escavador;

import jakarta.validation.constraints.NotNull;

public record MonitoramentoRequest(@NotNull FrequenciaMonitoramento frequencia) {
}
