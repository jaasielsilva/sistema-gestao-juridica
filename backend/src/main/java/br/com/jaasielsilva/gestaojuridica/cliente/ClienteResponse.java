package br.com.jaasielsilva.gestaojuridica.cliente;

import java.time.Instant;

public record ClienteResponse(
        Long id, TipoPessoa tipo, String nome, String documento, String email, String telefone, String cep,
        String logradouro, String numero, String complemento, String bairro, String cidade, String uf,
        String observacoes, boolean ativo, Instant criadoEm, Instant atualizadoEm) {

    static ClienteResponse of(Cliente c) {
        return new ClienteResponse(c.getId(), c.getTipo(), c.getNome(), c.getDocumento(), c.getEmail(),
                c.getTelefone(), c.getCep(), c.getLogradouro(), c.getNumero(), c.getComplemento(), c.getBairro(),
                c.getCidade(), c.getUf(), c.getObservacoes(), c.isAtivo(), c.getCriadoEm(), c.getAtualizadoEm());
    }
}
