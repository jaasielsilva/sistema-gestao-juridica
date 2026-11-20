package br.com.jaasielsilva.gestaojuridica.prazo;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class SituacaoPrazoTest {

    private static final LocalDate HOJE = LocalDate.of(2026, 11, 20);

    @Test
    void prazoAbertoSegueADataDeVencimento() {
        assertThat(SituacaoPrazo.de(StatusPrazo.ABERTO, HOJE, HOJE)).isEqualTo(SituacaoPrazo.HOJE);
        assertThat(SituacaoPrazo.de(StatusPrazo.ABERTO, HOJE.plusDays(1), HOJE)).isEqualTo(SituacaoPrazo.PROXIMO);
        assertThat(SituacaoPrazo.de(StatusPrazo.ABERTO, HOJE.minusDays(1), HOJE)).isEqualTo(SituacaoPrazo.VENCIDO);
    }

    @Test
    void prazoCumpridoNuncaEVencido() {
        assertThat(SituacaoPrazo.de(StatusPrazo.CUMPRIDO, HOJE.minusDays(30), HOJE)).isEqualTo(SituacaoPrazo.CUMPRIDO);
    }

    @Test
    void todoTipoDoCatalogoTemRotuloECategoria() {
        assertThat(Arrays.stream(TipoPrazo.values()))
                .allSatisfy(t -> {
                    assertThat(t.getRotulo()).isNotBlank();
                    assertThat(t.getCategoria()).isNotNull();
                });
        assertThat(TipoPrazo.daCategoria(CategoriaPrazo.RECURSOS)).contains(TipoPrazo.APELACAO, TipoPrazo.AGRAVO_INSTRUMENTO);
    }
}
