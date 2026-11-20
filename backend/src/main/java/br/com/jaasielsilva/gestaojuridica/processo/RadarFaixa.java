package br.com.jaasielsilva.gestaojuridica.processo;

/** Quao parado o processo esta na etapa atual: OK ate 3 dias, ATENCAO de 4 a 7, TRAVADO a partir de 8 (mais de 7). */
public enum RadarFaixa {
    OK,
    ATENCAO,
    TRAVADO;

    /** Dias inteiros na etapa a partir dos quais o processo deixa de ser OK. */
    public static final int DIAS_ATENCAO = 4;
    /** Dias inteiros na etapa a partir dos quais o processo e considerado travado. */
    public static final int DIAS_TRAVADO = 8;

    public static RadarFaixa de(long diasNaEtapa) {
        if (diasNaEtapa >= DIAS_TRAVADO) {
            return TRAVADO;
        }
        return diasNaEtapa >= DIAS_ATENCAO ? ATENCAO : OK;
    }
}
