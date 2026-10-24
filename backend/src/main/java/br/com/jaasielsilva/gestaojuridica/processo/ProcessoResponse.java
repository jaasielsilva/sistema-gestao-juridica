package br.com.jaasielsilva.gestaojuridica.processo;

import br.com.jaasielsilva.gestaojuridica.cliente.TipoPessoa;

import java.time.Instant;
import java.time.LocalDate;

public record ProcessoResponse(
        Long id,
        String numero,
        Long clienteId,
        String clienteNome,
        String clienteDocumento,
        TipoPessoa clienteTipo,
        TipoProcesso tipo,
        String areaJuridica,
        String tribunal,
        String comarca,
        String vara,
        String advogadoNome,
        String advogadoOab,
        SituacaoProcesso situacao,
        LocalDate proximoPrazo,
        String proximoPrazoDescricao,
        String observacoes,
        Instant criadoEm,
        Instant atualizadoEm) {

    static ProcessoResponse of(Processo p) {
        var c = p.getCliente();
        return new ProcessoResponse(p.getId(), p.getNumero(), c.getId(), c.getNome(), c.getDocumento(), c.getTipo(),
                p.getTipo(), p.getAreaJuridica(), p.getTribunal(), p.getComarca(), p.getVara(), p.getAdvogadoNome(),
                p.getAdvogadoOab(), p.getSituacao(), p.getProximoPrazo(), p.getProximoPrazoDescricao(),
                p.getObservacoes(), p.getCriadoEm(), p.getAtualizadoEm());
    }
}
