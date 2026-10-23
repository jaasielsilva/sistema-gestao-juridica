package br.com.jaasielsilva.gestaojuridica.cliente;

/** Normalizacao e validacao (digitos verificadores) de CPF e CNPJ. */
public final class Documento {

    private static final int[] PESOS_CNPJ_1 = {5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};
    private static final int[] PESOS_CNPJ_2 = {6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};

    private Documento() {
    }

    /** Remove tudo que nao for digito. */
    public static String somenteDigitos(String valor) {
        return valor == null ? "" : valor.replaceAll("\\D", "");
    }

    public static boolean valido(TipoPessoa tipo, String digitos) {
        return tipo == TipoPessoa.PF ? cpfValido(digitos) : cnpjValido(digitos);
    }

    static boolean cpfValido(String d) {
        if (d == null || !d.matches("\\d{11}") || repetido(d)) {
            return false;
        }
        return digitoCpf(d, 9) == d.charAt(9) - '0' && digitoCpf(d, 10) == d.charAt(10) - '0';
    }

    static boolean cnpjValido(String d) {
        if (d == null || !d.matches("\\d{14}") || repetido(d)) {
            return false;
        }
        return digito(d, PESOS_CNPJ_1) == d.charAt(12) - '0' && digito(d, PESOS_CNPJ_2) == d.charAt(13) - '0';
    }

    private static boolean repetido(String d) {
        return d.chars().distinct().count() == 1;
    }

    /** CPF: pesos decrescentes a partir de n + 1 sobre os n primeiros digitos. */
    private static int digitoCpf(String d, int n) {
        int soma = 0;
        for (int i = 0; i < n; i++) {
            soma += (d.charAt(i) - '0') * (n + 1 - i);
        }
        return resto(soma);
    }

    private static int digito(String d, int[] pesos) {
        int soma = 0;
        for (int i = 0; i < pesos.length; i++) {
            soma += (d.charAt(i) - '0') * pesos[i];
        }
        return resto(soma);
    }

    private static int resto(int soma) {
        int r = (soma * 10) % 11;
        return r == 10 ? 0 : r;
    }
}
