package br.com.jaasielsilva.gestaojuridica.prazo;

/** Agrupamento dos tipos de prazo, usado nos filtros e na organizacao do catalogo. */
public enum CategoriaPrazo {
    PETICAO_INICIAL("Petição inicial e regularização"),
    DEFESA("Defesa e contraditório"),
    PROVAS("Provas e perícia"),
    INTIMACOES("Intimações e despachos"),
    RECURSOS("Recursos"),
    EXECUCAO("Cumprimento de sentença e execução"),
    CUSTAS("Custas e despesas"),
    INCIDENTES("Incidentes processuais"),
    OUTROS("Outros");

    private final String rotulo;

    CategoriaPrazo(String rotulo) {
        this.rotulo = rotulo;
    }

    public String getRotulo() {
        return rotulo;
    }
}
