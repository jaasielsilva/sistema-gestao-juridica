package br.com.jaasielsilva.gestaojuridica.cliente;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Dados de cadastro/edicao. CPF/CNPJ, CEP e telefone podem vir com mascara; o service normaliza. */
public record ClienteRequest(
        @NotNull TipoPessoa tipo,
        @NotBlank @Size(max = 200) String nome,
        @NotBlank @Size(max = 18) String documento,
        @Email @Size(max = 200) String email,
        @Size(max = 20) String telefone,
        @Size(max = 9) String cep,
        @Size(max = 200) String logradouro,
        @Size(max = 20) String numero,
        @Size(max = 100) String complemento,
        @Size(max = 100) String bairro,
        @Size(max = 100) String cidade,
        @Pattern(regexp = "^[A-Za-z]{2}$", message = "UF invalida") String uf,
        @Size(max = 2000) String observacoes) {
}
