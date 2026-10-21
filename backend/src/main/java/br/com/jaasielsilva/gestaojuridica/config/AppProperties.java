package br.com.jaasielsilva.gestaojuridica.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.Locale;

/** Configuracao tipada de tudo que e especifico da aplicacao. Valida na partida: config errada = nao sobe. */
@Validated
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        @NotBlank String baseUrl,
        boolean registrationEnabled,
        @Valid Jwt jwt,
        @Valid Refresh refresh,
        @Valid Throttle throttle,
        @Valid Mfa mfa,
        @Valid Mail mail) {

    public record Jwt(
            @NotBlank @Size(min = 32, message = "app.jwt.secret precisa ter no minimo 32 caracteres") String secret,
            @NotBlank String issuer,
            @Positive long accessMinutes) {

        public Jwt {
            if (secret != null && secret.toLowerCase(Locale.ROOT).contains("troque")) {
                throw new IllegalArgumentException("app.jwt.secret nao pode conter marcadores de segredo de exemplo");
            }
        }
    }

    public record Refresh(@Positive long days, boolean cookieSecure, @PositiveOrZero long reuseGraceSeconds) {
    }

    public record Throttle(
            @Positive int maxLoginPerEmail,
            @Positive int maxLoginPerIp,
            @Positive int maxSensitivePerIp,
            @Positive long windowMinutes,
            @Positive long lockMinutes) {
    }

    public record Mfa(@NotBlank String issuer, @NotBlank String encryptionKey) {

        public Mfa {
            if (encryptionKey != null && encryptionKey.toLowerCase(Locale.ROOT).contains("troque")) {
                throw new IllegalArgumentException(
                        "app.mfa.encryption-key nao pode conter marcadores de segredo de exemplo. " +
                        "Gere uma chave real com: openssl rand -base64 32");
            }
        }
    }

    public record Mail(@NotBlank String from, boolean logBody) {
    }
}
