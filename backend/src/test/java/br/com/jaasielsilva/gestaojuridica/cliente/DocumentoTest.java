package br.com.jaasielsilva.gestaojuridica.cliente;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentoTest {

    @Test
    void removeMascaraDeCpfCnpjCepETelefone() {
        assertThat(Documento.somenteDigitos("529.982.247-25")).isEqualTo("52998224725");
        assertThat(Documento.somenteDigitos("11.222.333/0001-81")).isEqualTo("11222333000181");
        assertThat(Documento.somenteDigitos(null)).isEmpty();
    }

    @Test
    void aceitaCpfValido() {
        assertThat(Documento.valido(TipoPessoa.PF, "52998224725")).isTrue();
        assertThat(Documento.valido(TipoPessoa.PF, "11144477735")).isTrue();
    }

    @Test
    void rejeitaCpfComDigitoErradoTamanhoErradoOuRepetido() {
        assertThat(Documento.valido(TipoPessoa.PF, "52998224724")).isFalse();
        assertThat(Documento.valido(TipoPessoa.PF, "5299822472")).isFalse();
        assertThat(Documento.valido(TipoPessoa.PF, "11111111111")).isFalse();
        assertThat(Documento.valido(TipoPessoa.PF, "")).isFalse();
    }

    @Test
    void aceitaCnpjValido() {
        assertThat(Documento.valido(TipoPessoa.PJ, "11222333000181")).isTrue();
        assertThat(Documento.valido(TipoPessoa.PJ, "11444777000161")).isTrue();
    }

    @Test
    void rejeitaCnpjComDigitoErradoTamanhoErradoOuRepetido() {
        assertThat(Documento.valido(TipoPessoa.PJ, "11222333000180")).isFalse();
        assertThat(Documento.valido(TipoPessoa.PJ, "1122233300018")).isFalse();
        assertThat(Documento.valido(TipoPessoa.PJ, "00000000000000")).isFalse();
    }

    @Test
    void cpfNaoValidaComoCnpjENemOContrario() {
        assertThat(Documento.valido(TipoPessoa.PJ, "52998224725")).isFalse();
        assertThat(Documento.valido(TipoPessoa.PF, "11222333000181")).isFalse();
    }
}
