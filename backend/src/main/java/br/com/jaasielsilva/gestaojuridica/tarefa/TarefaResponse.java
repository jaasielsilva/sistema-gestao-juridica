package br.com.jaasielsilva.gestaojuridica.tarefa;

import java.time.Instant;
import java.time.LocalDate;

public record TarefaResponse(
        Long id,
        String titulo,
        String descricao,
        StatusTarefa status,
        PrioridadeTarefa prioridade,
        LocalDate prazo,
        boolean atrasada,
        Long processoId,
        String processoNumero,
        String clienteNome,
        Long responsavelId,
        String responsavelNome,
        int ordem,
        Long criadoPorId,
        Instant concluidaEm,
        Instant criadoEm) {

    static TarefaResponse of(Tarefa t, LocalDate hoje) {
        var processo = t.getProcesso();
        var responsavel = t.getResponsavel();
        boolean atrasada = t.getStatus() != StatusTarefa.CONCLUIDA && t.getPrazo() != null && t.getPrazo().isBefore(hoje);
        return new TarefaResponse(t.getId(), t.getTitulo(), t.getDescricao(), t.getStatus(), t.getPrioridade(),
                t.getPrazo(), atrasada, processo == null ? null : processo.getId(),
                processo == null ? null : processo.getNumero(),
                processo == null ? null : processo.getCliente().getNome(),
                responsavel == null ? null : responsavel.getId(), responsavel == null ? null : responsavel.getNome(),
                t.getOrdem(), t.getCriadoPorId(), t.getConcluidaEm(), t.getCriadoEm());
    }
}
