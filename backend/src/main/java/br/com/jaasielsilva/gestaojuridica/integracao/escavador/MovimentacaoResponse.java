package br.com.jaasielsilva.gestaojuridica.integracao.escavador;

import java.time.Instant;

public record MovimentacaoResponse(Long id, Instant dataMovimento, String tipoEvento, String descricao, String origem) {

    public static MovimentacaoResponse of(Movimentacao m) {
        return new MovimentacaoResponse(m.getId(), m.getDataMovimento(), m.getTipoEvento(), m.getDescricao(), m.getOrigem());
    }
}
