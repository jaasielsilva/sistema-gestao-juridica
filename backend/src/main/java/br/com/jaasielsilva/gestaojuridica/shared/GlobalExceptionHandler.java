package br.com.jaasielsilva.gestaojuridica.shared;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiError> business(BusinessException ex) {
        HttpStatus status = ex.getStatus();
        ResponseEntity.BodyBuilder response = ResponseEntity.status(status);
        if (ex instanceof TooManyRequestsException tooMany) {
            response.header("Retry-After", String.valueOf(tooMany.getRetryAfterSeconds()));
        }
        return response.body(new ApiError(java.time.Instant.now(), status.value(), status.getReasonPhrase(),
                ex.getMessage(), MDC.get(CorrelationIdFilter.MDC_KEY), List.of()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> validation(MethodArgumentNotValidException ex) {
        List<ApiError.FieldViolation> violations = ex.getBindingResult().getFieldErrors().stream()
                .map(f -> new ApiError.FieldViolation(f.getField(), f.getDefaultMessage()))
                .toList();
        return build(HttpStatus.BAD_REQUEST, "Dados invalidos", violations);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> unreadable(HttpMessageNotReadableException ex) {
        return build(HttpStatus.BAD_REQUEST, "Corpo da requisicao invalido", List.of());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> denied(AccessDeniedException ex) {
        return build(HttpStatus.FORBIDDEN, "Acesso negado", List.of());
    }

    @ExceptionHandler(br.com.jaasielsilva.gestaojuridica.tenant.TenantViolationException.class)
    public ResponseEntity<ApiError> crossTenant(br.com.jaasielsilva.gestaojuridica.tenant.TenantViolationException ex) {
        // Rede de seguranca: loga como erro (sinal de bug ou ataque) e responde como se o recurso nao existisse.
        log.error("{}", ex.getMessage());
        return build(HttpStatus.NOT_FOUND, "Recurso nao encontrado", List.of());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> unexpected(Exception ex) {
        // O detalhe fica so no log (com correlationId). O cliente recebe mensagem generica.
        log.error("Erro inesperado", ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Erro interno", List.of());
    }

    private ResponseEntity<ApiError> build(HttpStatus status, String message, List<ApiError.FieldViolation> violations) {
        ApiError body = new ApiError(java.time.Instant.now(), status.value(), status.getReasonPhrase(), message,
                MDC.get(CorrelationIdFilter.MDC_KEY), violations);
        return ResponseEntity.status(status).body(body);
    }
}
