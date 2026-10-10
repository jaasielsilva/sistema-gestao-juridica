package com.example.app.security;

import com.example.app.user.Papel;

/** Identidade autenticada extraida do token. E a unica fonte de verdade sobre "quem e o usuario". */
public record AuthPrincipal(Long userId, String email, Papel papel) {
}
