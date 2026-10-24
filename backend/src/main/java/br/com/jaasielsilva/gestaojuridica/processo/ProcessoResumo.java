package br.com.jaasielsilva.gestaojuridica.processo;

/** Contagens do cadastro de processos do tenant, para os cartoes de resumo da tela. */
public record ProcessoResumo(long total, long emAndamento, long suspensos, long encerrados, long urgentes) {
}
