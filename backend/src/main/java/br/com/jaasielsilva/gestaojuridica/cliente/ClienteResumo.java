package br.com.jaasielsilva.gestaojuridica.cliente;

/** Contagens do cadastro de clientes do tenant, para os cartoes de resumo da tela. */
public record ClienteResumo(long total, long ativos, long inativos, long pessoasFisicas, long pessoasJuridicas) {
}
