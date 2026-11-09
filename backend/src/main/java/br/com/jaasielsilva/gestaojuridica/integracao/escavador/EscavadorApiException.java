package br.com.jaasielsilva.gestaojuridica.integracao.escavador;

import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.RestClientResponseException;

/** Erro ao chamar a API do Escavador. Nunca propaga o corpo bruto da resposta (pode conter o token de volta em erro). */
public class EscavadorApiException extends RuntimeException {

    private final HttpStatusCode status;

    private EscavadorApiException(HttpStatusCode status, String message) {
        super(message);
        this.status = status;
    }

    static EscavadorApiException de(RestClientResponseException ex) {
        String msg = switch (ex.getStatusCode().value()) {
            case 401 -> "Token do Escavador invalido ou revogado";
            case 402 -> "Sem saldo na conta Escavador do escritorio";
            case 404 -> "Processo nao encontrado no Escavador";
            case 429 -> "Limite de requisicoes do Escavador excedido, tente novamente mais tarde";
            default -> "Falha ao comunicar com o Escavador";
        };
        return new EscavadorApiException(ex.getStatusCode(), msg);
    }

    public HttpStatusCode getStatus() {
        return status;
    }
}
