package br.com.jaasielsilva.gestaojuridica.tarefa;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** Destino do cartao: a coluna e a posicao nela (0 = topo). Posicao alem do fim vai para o fim. */
public record MoverTarefaRequest(@NotNull StatusTarefa status, @Min(0) int posicao) {
}
