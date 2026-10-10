package com.example.app.auth;

import com.example.app.user.Papel;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Contratos de entrada e saida do modulo de autenticacao. */
public final class AuthDtos {

    private AuthDtos() {
    }

    public record RegisterRequest(
            @NotBlank @Size(max = 150) String nome,
            @NotBlank @Email @Size(max = 255) String email,
            // 72 e o limite de bytes do BCrypt.
            @NotBlank @Size(min = 8, max = 72) String senha) {
    }

    public record LoginRequest(
            @NotBlank @Email String email,
            @NotBlank String senha) {
    }

    public record TokenResponse(String accessToken, String tokenType, long expiresInSeconds) {
    }

    public record UserResponse(Long id, String nome, String email, Papel papel) {
    }
}
