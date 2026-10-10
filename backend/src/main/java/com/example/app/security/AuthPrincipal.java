package com.example.app.security;

import com.example.app.user.Papel;

import java.util.UUID;

/** Identidade autenticada extraida do access token. E a unica fonte de verdade sobre "quem e o usuario e de qual tenant". */
public record AuthPrincipal(Long userId, String email, Papel papel, Long tenantId, UUID sessionId) {
}
