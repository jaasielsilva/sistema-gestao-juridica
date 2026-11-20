package br.com.jaasielsilva.gestaojuridica.processo;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/** Uma linha do radar: onde o processo esta e ha quantos dias. */
public record RadarItem(
        Long id,
        String numero,
        Long clienteId,
        String clienteNome,
        TipoProcesso tipo,
        String areaJuridica,
        SituacaoProcesso situacao,
        String advogadoNome,
        EtapaProcesso etapa,
        String etapaRotulo,
        Instant etapaAtualizadaEm,
        long diasNaEtapa,
        RadarFaixa faixa) {

    static RadarItem of(Processo p, Instant agora) {
        long dias = Math.max(0, ChronoUnit.DAYS.between(p.getEtapaAtualizadaEm(), agora));
        var c = p.getCliente();
        return new RadarItem(p.getId(), p.getNumero(), c.getId(), c.getNome(), p.getTipo(), p.getAreaJuridica(),
                p.getSituacao(), p.getAdvogadoNome(), p.getEtapa(), p.getEtapa().getRotulo(), p.getEtapaAtualizadaEm(),
                dias, RadarFaixa.de(dias));
    }
}
