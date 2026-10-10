package com.example.app.security;

import com.example.app.auth.session.SessaoRepository;
import com.example.app.tenant.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.List;

/**
 * Autentica pelo Bearer token e confirma que a SESSAO continua ativa (logout/revogacao valem na hora,
 * sem esperar o access token expirar). Tambem define o tenant da requisicao a partir do token.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final SessaoRepository sessoes;

    public JwtAuthenticationFilter(JwtService jwtService, SessaoRepository sessoes) {
        this.jwtService = jwtService;
        this.sessoes = sessoes;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        try {
            String header = request.getHeader(HttpHeaders.AUTHORIZATION);
            if (header != null && header.startsWith(PREFIX)) {
                jwtService.parseAccess(header.substring(PREFIX.length()))
                        .filter(p -> sessoes.estaAtiva(p.sessionId(), Instant.now()))
                        .ifPresent(principal -> {
                            var auth = new UsernamePasswordAuthenticationToken(principal, null,
                                    List.of(new SimpleGrantedAuthority("ROLE_" + principal.papel().name())));
                            SecurityContextHolder.getContext().setAuthentication(auth);
                            TenantContext.set(principal.tenantId());
                        });
            }
            chain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }
}
