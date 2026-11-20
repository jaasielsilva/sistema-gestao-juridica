package br.com.jaasielsilva.gestaojuridica.prazo;

import java.time.Instant;
import java.time.LocalDate;

public record PrazoResponse(
        Long id,
        Long processoId,
        String processoNumero,
        Long clienteId,
        String clienteNome,
        TipoPrazo tipo,
        String tipoRotulo,
        CategoriaPrazo categoria,
        String categoriaRotulo,
        String descricao,
        LocalDate dataVencimento,
        StatusPrazo status,
        SituacaoPrazo situacao,
        Long responsavelId,
        String responsavelNome,
        Instant cumpridoEm,
        Instant criadoEm) {

    static PrazoResponse of(Prazo p, LocalDate hoje) {
        var processo = p.getProcesso();
        var cliente = processo.getCliente();
        var responsavel = p.getResponsavel();
        return new PrazoResponse(p.getId(), processo.getId(), processo.getNumero(), cliente.getId(), cliente.getNome(),
                p.getTipo(), p.getTipo().getRotulo(), p.getTipo().getCategoria(),
                p.getTipo().getCategoria().getRotulo(), p.getDescricao(), p.getDataVencimento(), p.getStatus(),
                SituacaoPrazo.de(p.getStatus(), p.getDataVencimento(), hoje),
                responsavel == null ? null : responsavel.getId(), responsavel == null ? null : responsavel.getNome(),
                p.getCumpridoEm(), p.getCriadoEm());
    }
}
