package br.com.jaasielsilva.gestaojuridica.integracao.escavador;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.Map;

/**
 * Cliente HTTP fechado para a API V1 do Escavador. Base URL fixa; cada metodo monta o path a partir
 * de parametros tipados — NUNCA aceita uma URL completa como entrada. Isso e deliberado: a SSRF
 * confirmada no LexCRM (SEC-007, ver ADR 0007) veio de reusar um parametro de paginacao vindo do
 * CLIENTE como URL literal da proxima chamada autenticada. Aqui nao existe caminho para isso.
 */
@Component
public class EscavadorClient {

    private static final String BASE_URL = "https://api.escavador.com";

    private final RestClient restClient;

    public EscavadorClient() {
        this.restClient = RestClient.builder().baseUrl(BASE_URL).build();
    }

    public MonitoramentoResposta criarMonitoramento(String token, String numeroCnj, FrequenciaMonitoramento frequencia) {
        try {
            return restClient.post()
                    .uri("/api/v1/monitoramentos-tribunal")
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("tipo", "UNICO", "valor", numeroCnj, "frequencia", frequencia.name()))
                    .retrieve()
                    .body(MonitoramentoResposta.class);
        } catch (RestClientResponseException ex) {
            throw EscavadorApiException.de(ex);
        }
    }

    public void editarFrequencia(String token, long monitoramentoId, FrequenciaMonitoramento frequencia) {
        try {
            restClient.put()
                    .uri("/api/v1/monitoramentos-tribunal/{id}", monitoramentoId)
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("frequencia", frequencia.name()))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException ex) {
            throw EscavadorApiException.de(ex);
        }
    }

    public void removerMonitoramento(String token, long monitoramentoId) {
        try {
            restClient.delete()
                    .uri("/api/v1/monitoramentos-tribunal/{id}", monitoramentoId)
                    .header("Authorization", "Bearer " + token)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException ex) {
            throw EscavadorApiException.de(ex);
        }
    }

    /** So os campos que realmente usamos da resposta do Escavador. */
    public record MonitoramentoResposta(Long id, Monitor monitor) {
        public record Monitor(String status) {
        }
    }
}
