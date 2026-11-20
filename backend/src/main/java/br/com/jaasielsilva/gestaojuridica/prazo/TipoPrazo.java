package br.com.jaasielsilva.gestaojuridica.prazo;

import java.util.Arrays;
import java.util.List;

import static br.com.jaasielsilva.gestaojuridica.prazo.CategoriaPrazo.*;

/**
 * Catalogo de tipos de prazo processual. O nome do enum e o que vai para o banco; o rotulo e so para exibicao.
 * O frontend le o catalogo por GET /api/v1/prazos/tipos, entao acrescentar um tipo aqui nao exige mudar a tela.
 */
public enum TipoPrazo {
    EMENDA_PETICAO_INICIAL("Emenda à petição inicial", PETICAO_INICIAL),
    REGULARIZACAO_REPRESENTACAO("Regularização da representação processual", PETICAO_INICIAL),
    JUNTADA_PROCURACAO("Juntada de procuração", PETICAO_INICIAL),
    COMPLEMENTACAO_DOCUMENTOS("Complementação de documentos", PETICAO_INICIAL),
    RETIFICACAO_VALOR_CAUSA("Retificação do valor da causa", PETICAO_INICIAL),

    CONTESTACAO("Contestação", DEFESA),
    REPLICA("Réplica / impugnação à contestação", DEFESA),
    RESPOSTA_RECONVENCAO("Resposta à reconvenção", DEFESA),
    MANIFESTACAO_DOCUMENTOS("Manifestação sobre documentos da parte contrária", DEFESA),

    ESPECIFICACAO_PROVAS("Especificação de provas", PROVAS),
    QUESITOS_PERICIAIS("Apresentação de quesitos periciais", PROVAS),
    ASSISTENTE_TECNICO("Indicação de assistente técnico", PROVAS),
    MANIFESTACAO_LAUDO("Manifestação sobre laudo pericial", PROVAS),
    IMPUGNACAO_LAUDO("Impugnação ao laudo pericial", PROVAS),
    ALEGACOES_FINAIS("Alegações finais", PROVAS),

    MANIFESTACAO_DESPACHO("Manifestação sobre despacho", INTIMACOES),
    CUMPRIMENTO_DETERMINACAO("Cumprimento de determinação judicial", INTIMACOES),
    MANIFESTACAO_INTIMACAO("Manifestação sobre intimação judicial", INTIMACOES),
    JUNTADA_DETERMINACAO("Juntada por determinação judicial", INTIMACOES),

    EMBARGOS_DECLARACAO("Embargos de declaração", RECURSOS),
    APELACAO("Apelação", RECURSOS),
    RECURSO_INOMINADO("Recurso inominado", RECURSOS),
    AGRAVO_INSTRUMENTO("Agravo de instrumento", RECURSOS),
    RECURSO_ORDINARIO("Recurso ordinário trabalhista", RECURSOS),
    RECURSO_ESPECIAL("Recurso especial", RECURSOS),
    CONTRARRAZOES("Contrarrazões", RECURSOS),

    APRESENTACAO_CALCULOS("Apresentação de cálculos", EXECUCAO),
    IMPUGNACAO_CALCULOS("Impugnação aos cálculos", EXECUCAO),
    OBRIGACAO_FAZER("Cumprimento de obrigação de fazer", EXECUCAO),
    EXPEDICAO_RPV("Expedição de RPV", EXECUCAO),
    MANIFESTACAO_PENHORA("Manifestação sobre penhora", EXECUCAO),

    CUSTAS_INICIAIS("Custas iniciais", CUSTAS),
    COMPLEMENTACAO_CUSTAS("Complementação de custas", CUSTAS),
    PREPARO_RECURSAL("Preparo recursal", CUSTAS),
    HONORARIOS_PERICIAIS("Honorários periciais", CUSTAS),

    CUMPRIMENTO_LIMINAR("Cumprimento de liminar", INCIDENTES),
    HABILITACAO_SUCESSORES("Habilitação de sucessores", INCIDENTES),
    MANIFESTACAO_ACORDO("Manifestação sobre acordo", INCIDENTES),
    DESCONSIDERACAO_PERSONALIDADE("Incidente de desconsideração da personalidade jurídica", INCIDENTES),

    /** Qualquer prazo fora do catalogo; exige descricao. */
    OUTRO("Outro (descrever)", OUTROS);

    private final String rotulo;
    private final CategoriaPrazo categoria;

    TipoPrazo(String rotulo, CategoriaPrazo categoria) {
        this.rotulo = rotulo;
        this.categoria = categoria;
    }

    public String getRotulo() {
        return rotulo;
    }

    public CategoriaPrazo getCategoria() {
        return categoria;
    }

    public static List<TipoPrazo> daCategoria(CategoriaPrazo categoria) {
        return Arrays.stream(values()).filter(t -> t.categoria == categoria).toList();
    }

    /** Tipos cujo rotulo contem o termo, sem diferenciar maiusculas nem acentos ("contestacao" acha "Contestação"). */
    public static List<TipoPrazo> buscarPorRotulo(String termo) {
        String procurado = semAcento(termo);
        if (procurado.isEmpty()) {
            return List.of();
        }
        return Arrays.stream(values()).filter(t -> semAcento(t.rotulo).contains(procurado)).toList();
    }

    private static String semAcento(String s) {
        return java.text.Normalizer.normalize(s == null ? "" : s.trim(), java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toLowerCase(java.util.Locale.ROOT);
    }
}
