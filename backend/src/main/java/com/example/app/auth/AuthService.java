package com.example.app.auth;

import com.example.app.auth.AuthDtos.LoginRequest;
import com.example.app.auth.AuthDtos.RegisterRequest;
import com.example.app.auth.AuthDtos.TokenResponse;
import com.example.app.auth.AuthDtos.UserResponse;
import com.example.app.security.JwtService;
import com.example.app.shared.BusinessException;
import com.example.app.user.Papel;
import com.example.app.user.Usuario;
import com.example.app.user.UsuarioRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.UUID;

@Service
public class AuthService {

    private final UsuarioRepository usuarios;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    // Hash descartavel: gasta o mesmo tempo de BCrypt quando o e-mail nao existe (evita enumeracao por timing).
    private final String dummyHash;

    public AuthService(UsuarioRepository usuarios, PasswordEncoder encoder, JwtService jwt) {
        this.usuarios = usuarios;
        this.encoder = encoder;
        this.jwt = jwt;
        this.dummyHash = encoder.encode(UUID.randomUUID().toString());
    }

    @Transactional
    public UserResponse register(RegisterRequest req) {
        String email = normalize(req.email());
        if (usuarios.existsByEmail(email)) {
            throw BusinessException.conflict("E-mail ja cadastrado");
        }
        try {
            // Papel e sempre USER no auto-cadastro; ADMIN nunca vem do cliente.
            Usuario u = usuarios.saveAndFlush(new Usuario(req.nome().trim(), email, encoder.encode(req.senha()), Papel.USER));
            return toResponse(u);
        } catch (DataIntegrityViolationException ex) {
            // Corrida entre duas requisicoes com o mesmo e-mail: a constraint unique do banco decide.
            throw BusinessException.conflict("E-mail ja cadastrado");
        }
    }

    @Transactional(readOnly = true)
    public TokenResponse login(LoginRequest req) {
        Usuario u = usuarios.findByEmail(normalize(req.email())).orElse(null);
        boolean ok = encoder.matches(req.senha(), u != null ? u.getSenhaHash() : dummyHash);
        // Mensagem unica para e-mail inexistente, senha errada ou usuario inativo (nao revela qual).
        if (u == null || !ok || !u.isAtivo()) {
            throw BusinessException.unauthorized("Credenciais invalidas");
        }
        return new TokenResponse(jwt.generate(u.getId(), u.getEmail(), u.getPapel()), "Bearer", jwt.expiresInSeconds());
    }

    @Transactional(readOnly = true)
    public UserResponse me(Long userId) {
        return usuarios.findById(userId)
                .filter(Usuario::isAtivo)
                .map(AuthService::toResponse)
                .orElseThrow(() -> BusinessException.unauthorized("Usuario nao encontrado"));
    }

    private static String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static UserResponse toResponse(Usuario u) {
        return new UserResponse(u.getId(), u.getNome(), u.getEmail(), u.getPapel());
    }
}
