package br.com.jaasielsilva.gestaojuridica.prazo;

/** Cartoes da tela de prazos. "proximos" = abertos com vencimento depois de hoje; "cumpridos" = baixados nos ultimos 30 dias. */
public record PrazoResumo(long hoje, long proximos, long vencidos, long cumpridosUltimos30Dias) {
}
