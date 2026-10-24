package br.com.jaasielsilva.gestaojuridica.processo;

import java.math.BigInteger;

/**
 * Numeracao unica CNJ (Resolucao 65/2008): NNNNNNN-DD.AAAA.J.TR.OOOO.
 * O digito verificador usa modulo 97 base 10 (ISO 7064).
 */
public final class NumeroCnj {

    private static final BigInteger NOVENTA_SETE = BigInteger.valueOf(97);

    private NumeroCnj() {
    }

    public static String somenteDigitos(String valor) {
        return valor == null ? "" : valor.replaceAll("\\D", "");
    }

    public static boolean valido(String valor) {
        String d = somenteDigitos(valor);
        if (d.length() != 20) {
            return false;
        }
        String paraVerificar = d.substring(0, 7) + d.substring(9) + d.substring(7, 9);
        return new BigInteger(paraVerificar).mod(NOVENTA_SETE).intValue() == 1;
    }

    /** Sequencial (7) + ano (4) + segmento (1) + tribunal (2) + origem (4) = 18 digitos, sem o DV. */
    static String digitoVerificador(String corpo18) {
        BigInteger n = new BigInteger(corpo18 + "00");
        int dv = 98 - n.mod(NOVENTA_SETE).intValue();
        return String.format("%02d", dv);
    }
}
