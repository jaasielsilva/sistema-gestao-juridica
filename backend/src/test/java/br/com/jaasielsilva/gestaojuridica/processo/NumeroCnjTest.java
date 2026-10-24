package br.com.jaasielsilva.gestaojuridica.processo;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NumeroCnjTest {

    @Test
    void aceitaNumeroCnjValidoComMascara() {
        assertThat(NumeroCnj.valido("0000009-15.2015.8.26.0558")).isTrue();
        assertThat(NumeroCnj.valido("00000091520158260558")).isTrue();
    }

    @Test
    void rejeitaDigitoVerificadorErradoTamanhoEVazio() {
        assertThat(NumeroCnj.valido("0000009-16.2015.8.26.0558")).isFalse();
        assertThat(NumeroCnj.valido("123")).isFalse();
        assertThat(NumeroCnj.valido("")).isFalse();
        assertThat(NumeroCnj.valido(null)).isFalse();
    }

    @Test
    void calculaDigitoConformeResolucao65() {
        assertThat(NumeroCnj.digitoVerificador("000000920158260558")).isEqualTo("15");
        assertThat(NumeroCnj.digitoVerificador("000123420248260100")).isEqualTo("71");
    }
}
