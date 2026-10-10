package com.example.app.auth;

import com.example.app.auth.AuthDtos.LoginRequest;
import com.example.app.auth.AuthDtos.RegisterRequest;
import com.example.app.security.JwtProperties;
import com.example.app.security.JwtService;
import com.example.app.shared.BusinessException;
import com.example.app.user.Papel;
import com.example.app.user.Usuario;
import com.example.app.user.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthServiceTest {

    private UsuarioRepository repo;
    private BCryptPasswordEncoder encoder;
    private AuthService service;

    @BeforeEach
    void setUp() {
        repo = mock(UsuarioRepository.class);
        encoder = new BCryptPasswordEncoder(4); // custo baixo so para teste rapido
        JwtService jwt = new JwtService(new JwtProperties("unit-test-secret-unit-test-secret-1234", "app", 5));
        service = new AuthService(repo, encoder, jwt);
    }

    @Test
    void registerNormalizaEmailGravaHashEForcaPapelUser() {
        when(repo.existsByEmail("joao@x.com")).thenReturn(false);
        when(repo.saveAndFlush(any(Usuario.class))).thenAnswer(i -> i.getArgument(0));

        service.register(new RegisterRequest("  Joao ", "  Joao@X.com ", "senha-forte-123"));

        ArgumentCaptor<Usuario> saved = ArgumentCaptor.forClass(Usuario.class);
        verify(repo).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getEmail()).isEqualTo("joao@x.com");
        assertThat(saved.getValue().getNome()).isEqualTo("Joao");
        assertThat(saved.getValue().getPapel()).isEqualTo(Papel.USER);
        assertThat(saved.getValue().getSenhaHash()).isNotEqualTo("senha-forte-123");
        assertThat(encoder.matches("senha-forte-123", saved.getValue().getSenhaHash())).isTrue();
    }

    @Test
    void registerComEmailExistenteRetornaConflito() {
        when(repo.existsByEmail("a@b.com")).thenReturn(true);

        assertThatThrownBy(() -> service.register(new RegisterRequest("A", "a@b.com", "senha-forte-123")))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void loginComCredenciaisCorretasRetornaToken() {
        Usuario u = new Usuario("A", "a@b.com", encoder.encode("senha-forte-123"), Papel.USER);
        when(repo.findByEmail("a@b.com")).thenReturn(Optional.of(u));

        var token = service.login(new LoginRequest("A@b.com", "senha-forte-123"));

        assertThat(token.accessToken()).isNotBlank();
        assertThat(token.tokenType()).isEqualTo("Bearer");
    }

    @Test
    void loginComSenhaErradaOuEmailInexistenteDevolveMesmoErro() {
        Usuario u = new Usuario("A", "a@b.com", encoder.encode("senha-forte-123"), Papel.USER);
        when(repo.findByEmail("a@b.com")).thenReturn(Optional.of(u));
        when(repo.findByEmail("naoexiste@b.com")).thenReturn(Optional.empty());

        for (LoginRequest req : new LoginRequest[]{
                new LoginRequest("a@b.com", "errada"), new LoginRequest("naoexiste@b.com", "qualquer")}) {
            assertThatThrownBy(() -> service.login(req))
                    .isInstanceOfSatisfying(BusinessException.class, e -> {
                        assertThat(e.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
                        assertThat(e.getMessage()).isEqualTo("Credenciais invalidas");
                    });
        }
    }
}
