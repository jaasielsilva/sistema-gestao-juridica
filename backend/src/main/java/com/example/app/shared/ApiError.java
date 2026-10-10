package com.example.app.shared;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

/** Contrato unico de erro da API. Nunca carrega stack trace, SQL ou detalhes internos. */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String message,
        String correlationId,
        List<FieldViolation> violations) {

    public record FieldViolation(String field, String message) {
    }

    public static ApiError of(int status, String error, String message, String correlationId) {
        return new ApiError(Instant.now(), status, error, message, correlationId, List.of());
    }
}
