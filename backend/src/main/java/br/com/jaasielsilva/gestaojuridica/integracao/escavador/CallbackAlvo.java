package br.com.jaasielsilva.gestaojuridica.integracao.escavador;

/** Resultado minimo da busca por slug de callback: so o necessario para validar e resolver o tenant. */
record CallbackAlvo(Long tenantId, String callbackSecretCifrado, boolean habilitado) {
}
