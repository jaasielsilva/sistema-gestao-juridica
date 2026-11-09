package br.com.jaasielsilva.gestaojuridica.integracao.escavador;

/**
 * Nunca inclui o token ou o callback secret — so a URL de callback (precisa ser colada no painel do
 * Escavador) e os dados nao sensiveis de configuracao.
 */
public record EscavadorConfiguracaoResponse(
        boolean configurado,
        boolean habilitado,
        long saldoLimiteCentavos,
        long gastoMensalEstimadoCentavos,
        String urlCallback) {

    public static EscavadorConfiguracaoResponse naoConfigurado() {
        return new EscavadorConfiguracaoResponse(false, false, 0, 0, null);
    }
}
