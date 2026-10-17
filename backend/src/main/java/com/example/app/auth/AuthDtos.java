package com.example.app.auth;

import com.example.app.user.Papel;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Contratos de entrada e saida do modulo de autenticacao. */
public final class AuthDtos {

    /** Tamanho minimo da senha (NIST: comprimento importa mais que regras de composicao). 72 = limite de bytes do BCrypt. */
    public static final int SENHA_MIN = 10;
    public static final int SENHA_MAX = 72;

    private AuthDtos() {
    }

    public record RegisterRequest(
            @NotBlank @Size(max = 150) String nome,
            @NotBlank @Size(max = 150) String organizacao,
            @NotBlank @Email @Size(max = 255) String email,
            @NotBlank @Size(min = SENHA_MIN, max = SENHA_MAX) String senha) {
    }

    public record LoginRequest(
            @NotBlank @Email String email,
            @NotBlank @Size(max = 200) String senha) {
    }

    /** Ou vem o access token (login concluido), ou mfaRequired + mfaToken (falta o segundo fator). O refresh vai em cookie. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record LoginResponse(String accessToken, String tokenType, Long expiresInSeconds,
                                Boolean mfaRequired, String mfaToken) {
    }

    public record MfaVerifyRequest(@NotBlank @Size(max = 600) String mfaToken, @NotBlank @Size(max = 20) String codigo) {
    }

    public record EmailRequest(@NotBlank @Email @Size(max = 255) String email) {
    }

    public record TokenRequest(@NotBlank @Size(max = 200) String token) {
    }

    public record ResetPasswordRequest(
            @NotBlank @Size(max = 200) String token,
            @NotBlank @Size(min = SENHA_MIN, max = SENHA_MAX) String novaSenha) {
    }

    public record ChangePasswordRequest(
            @NotBlank @Size(max = 200) String senhaAtual,
            @NotBlank @Size(min = SENHA_MIN, max = SENHA_MAX) String novaSenha) {
    }

    public record MfaEnableRequest(@NotBlank @Size(max = 20) String codigo) {
    }

    public record MfaDisableRequest(@NotBlank @Size(max = 200) String senha, @NotBlank @Size(max = 20) String codigo) {
    }

    public record MfaSetupResponse(String secret, String otpauthUri) {
    }

    public record RecoveryCodesResponse(List<String> codigos) {
    }

    public record UserResponse(Long id, String nome, String email, Papel papel, Long tenantId,
                               boolean emailVerificado, boolean mfaAtivo) {
    }

    public record AuthConfigResponse(boolean registrationEnabled) {
    }

    public record MessageResponse(String message) {
    }
}
