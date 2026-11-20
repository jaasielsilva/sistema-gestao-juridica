package br.com.jaasielsilva.gestaojuridica.processo;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RadarFaixaTest {

    @Test
    void ateTresDiasEOk() {
        assertThat(RadarFaixa.de(0)).isEqualTo(RadarFaixa.OK);
        assertThat(RadarFaixa.de(3)).isEqualTo(RadarFaixa.OK);
    }

    @Test
    void deQuatroASeteDiasEAtencao() {
        assertThat(RadarFaixa.de(4)).isEqualTo(RadarFaixa.ATENCAO);
        assertThat(RadarFaixa.de(7)).isEqualTo(RadarFaixa.ATENCAO);
    }

    @Test
    void maisDeSeteDiasEstaTravado() {
        assertThat(RadarFaixa.de(8)).isEqualTo(RadarFaixa.TRAVADO);
        assertThat(RadarFaixa.de(93)).isEqualTo(RadarFaixa.TRAVADO);
    }
}
