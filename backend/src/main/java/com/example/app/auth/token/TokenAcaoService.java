package com.example.app.auth.token;

import com.example.app.shared.Hashing;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/** Tokens de uso unico (verificacao de e-mail, redefinicao de senha). No banco fica so o hash SHA-256. */
@Service
public class TokenAcaoService {

    private final TokenAcaoRepository repo;

    public TokenAcaoService(TokenAcaoRepository repo) {
        this.repo = repo;
    }

    /** Emite um token novo e invalida os pendentes do mesmo tipo. Devolve o valor ORIGINAL (para o e-mail). */
    @Transactional
    public String emitir(Long usuarioId, TipoToken tipo, Duration validade) {
        Instant agora = Instant.now();
        repo.invalidarPendentes(usuarioId, tipo, agora);
        String raw = Hashing.randomToken(32);
        repo.save(new TokenAcao(usuarioId, tipo, Hashing.sha256Hex(raw), agora.plus(validade)));
        return raw;
    }

    /** Consome o token se for valido (existe, do tipo certo, nao usado, nao expirado). Retorna o id do usuario. */
    @Transactional
    public Optional<Long> consumir(String raw, TipoToken tipo) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        String hash = Hashing.sha256Hex(raw);
        if (repo.consumir(hash, tipo, Instant.now()) != 1) {
            return Optional.empty();
        }
        return repo.usuarioDoToken(hash);
    }
}
