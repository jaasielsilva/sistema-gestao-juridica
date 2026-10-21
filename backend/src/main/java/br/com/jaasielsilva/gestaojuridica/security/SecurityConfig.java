package br.com.jaasielsilva.gestaojuridica.security;

import br.com.jaasielsilva.gestaojuridica.auth.session.SessaoRepository;
import br.com.jaasielsilva.gestaojuridica.shared.ApiError;
import br.com.jaasielsilva.gestaojuridica.shared.CorrelationIdFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.io.IOException;
import java.util.List;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    /** Endpoints publicos de autenticacao (todos POST, exceto /config). Qualquer outra rota exige login. */
    private static final String[] PUBLIC_POST = {
            "/api/v1/auth/register", "/api/v1/auth/login", "/api/v1/auth/refresh", "/api/v1/auth/logout",
            "/api/v1/auth/mfa/verify", "/api/v1/auth/forgot-password", "/api/v1/auth/reset-password",
            "/api/v1/auth/verify-email", "/api/v1/auth/resend-verification"
    };

    /** Paths do Swagger/OpenAPI — liberados SOMENTE quando springdoc.swagger-ui.enabled=true (SEC-001). */
    private static final String[] SWAGGER_PATHS = {
            "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html"
    };

    @Value("${springdoc.swagger-ui.enabled:false}")
    private boolean swaggerEnabled;

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http, JwtService jwtService, SessaoRepository sessoes,
                                    ObjectMapper mapper) throws Exception {
        http
                // Access token vai no header Authorization (nao em cookie) => sem CSRF nessas rotas.
                // Os endpoints que usam o cookie de refresh exigem X-Requested-With e o cookie e SameSite=Strict.
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(h -> h
                        .httpStrictTransportSecurity(hsts -> hsts.includeSubDomains(true).maxAgeInSeconds(63_072_000))
                        .contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'none'; frame-ancestors 'none'"))
                        .referrerPolicy(r -> r.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER)))
                .authorizeHttpRequests(a -> {
                    a.requestMatchers(HttpMethod.POST, PUBLIC_POST).permitAll()
                     .requestMatchers(HttpMethod.GET, "/api/v1/auth/config").permitAll()
                     .requestMatchers("/actuator/health", "/actuator/health/liveness", "/actuator/health/readiness").permitAll();
                    // Swagger: so acessivel quando explicitamente habilitado (nunca em producao) — SEC-001.
                    if (swaggerEnabled) {
                        a.requestMatchers(SWAGGER_PATHS).permitAll();
                    }
                    a.requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                     .anyRequest().authenticated();
                })
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((req, res, ex) ->
                                write(res, mapper, HttpStatus.UNAUTHORIZED, "Nao autenticado"))
                        .accessDeniedHandler((req, res, ex) ->
                                write(res, mapper, HttpStatus.FORBIDDEN, "Acesso negado")))
                .addFilterBefore(new JwtAuthenticationFilter(jwtService, sessoes), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(@Value("${app.cors.allowed-origins}") List<String> origins) {
        CorsConfiguration cfg = new CorsConfiguration();
        cfg.setAllowedOrigins(origins);
        cfg.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        cfg.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Requested-With", CorrelationIdFilter.HEADER));
        cfg.setExposedHeaders(List.of(CorrelationIdFilter.HEADER, "Retry-After"));
        cfg.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cfg);
        return source;
    }

    private static void write(jakarta.servlet.http.HttpServletResponse res, ObjectMapper mapper,
                              HttpStatus status, String message) throws IOException {
        res.setStatus(status.value());
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        mapper.writeValue(res.getOutputStream(),
                ApiError.of(status.value(), status.getReasonPhrase(), message, MDC.get(CorrelationIdFilter.MDC_KEY)));
    }
}
