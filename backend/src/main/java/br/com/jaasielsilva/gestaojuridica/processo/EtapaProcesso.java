package br.com.jaasielsilva.gestaojuridica.processo;

/** Etapas do fluxo de um processo no escritorio, na ordem em que normalmente acontecem. */
public enum EtapaProcesso {
    CONTATO("Contato"),
    ANALISE("Análise"),
    ELABORACAO_PECA("Elaboração de peça"),
    PROTOCOLADO("Protocolado"),
    AGUARDANDO_DECISAO("Aguardando decisão"),
    RECURSO("Recurso"),
    CUMPRIMENTO("Cumprimento"),
    CONCLUSAO("Conclusão");

    private final String rotulo;

    EtapaProcesso(String rotulo) {
        this.rotulo = rotulo;
    }

    public String getRotulo() {
        return rotulo;
    }
}
