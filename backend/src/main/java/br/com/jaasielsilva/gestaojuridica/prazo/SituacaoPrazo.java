package br.com.jaasielsilva.gestaojuridica.prazo;

import java.time.LocalDate;

/** Situacao do prazo em relacao a "hoje" (calculada a cada leitura, nunca gravada: nao fica velha de um dia para o outro). */
public enum SituacaoPrazo {
    HOJE,
    PROXIMO,
    VENCIDO,
    CUMPRIDO;

    public static SituacaoPrazo de(StatusPrazo status, LocalDate vencimento, LocalDate hoje) {
        if (status == StatusPrazo.CUMPRIDO) {
            return CUMPRIDO;
        }
        if (vencimento.isBefore(hoje)) {
            return VENCIDO;
        }
        return vencimento.isEqual(hoje) ? HOJE : PROXIMO;
    }
}
