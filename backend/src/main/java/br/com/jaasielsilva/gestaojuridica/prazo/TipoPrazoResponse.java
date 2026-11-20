package br.com.jaasielsilva.gestaojuridica.prazo;

/** Item do catalogo de tipos de prazo (codigo = nome do enum). */
public record TipoPrazoResponse(TipoPrazo codigo, String rotulo, CategoriaPrazo categoria, String categoriaRotulo) {

    static TipoPrazoResponse of(TipoPrazo t) {
        return new TipoPrazoResponse(t, t.getRotulo(), t.getCategoria(), t.getCategoria().getRotulo());
    }
}
