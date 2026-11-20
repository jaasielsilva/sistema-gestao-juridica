package br.com.jaasielsilva.gestaojuridica.tarefa;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/** Cadastro/edicao de tarefa. A coluna so muda por /mover; no cadastro, status vazio = A_FAZER. */
public record TarefaRequest(
        @NotBlank @Size(max = 150) String titulo,
        @Size(max = 2000) String descricao,
        @NotNull PrioridadeTarefa prioridade,
        LocalDate prazo,
        Long processoId,
        Long responsavelId,
        StatusTarefa status) {
}
