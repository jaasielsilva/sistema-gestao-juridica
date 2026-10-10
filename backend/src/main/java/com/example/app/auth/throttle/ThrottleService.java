package com.example.app.auth.throttle;

import com.example.app.config.AppProperties;
import com.example.app.shared.Hashing;
import com.example.app.shared.TooManyRequestsException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * Limite de tentativas com bloqueio temporario, guardado no banco (vale com varias instancias da API).
 * Cada operacao de escrita roda em transacao PROPRIA: o registro de uma falha nao e desfeito
 * quando a requisicao termina com erro (que e justamente o caso de uso).
 */
@Service
public class ThrottleService {

    private final LoginTentativaRepository repo;
    private final AppProperties.Throttle cfg;
    private final TransactionTemplate tx;

    public ThrottleService(LoginTentativaRepository repo, AppProperties props, PlatformTransactionManager tm) {
        this.repo = repo;
        this.cfg = props.throttle();
        this.tx = new TransactionTemplate(tm);
        this.tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** Chave por e-mail: guarda o HASH, nao o endereco. */
    public static String emailKey(String scope, String email) {
        return scope + ":email:" + Hashing.sha256Hex(email);
    }

    public static String ipKey(String scope, String ip) {
        return scope + ":ip:" + (ip == null ? "desconhecido" : ip);
    }

    public static String userKey(String scope, Long userId) {
        return scope + ":user:" + userId;
    }

    /** Lanca 429 se alguma das chaves esta bloqueada agora. */
    public void assertNotBlocked(String... keys) {
        Instant now = Instant.now();
        List<LoginTentativa> blocked = repo.bloqueadas(Arrays.asList(keys), now);
        blocked.stream()
                .map(LoginTentativa::getBloqueadoAte)
                .max(Comparator.naturalOrder())
                .ifPresent(until -> {
                    throw new TooManyRequestsException(Duration.between(now, until).toSeconds() + 1);
                });
    }

    /** Conta uma tentativa (normalmente uma falha) e bloqueia ao atingir o maximo na janela. */
    public void hit(String key, int max) {
        try {
            tx.executeWithoutResult(s -> doHit(key, max));
        } catch (DataIntegrityViolationException racedInsert) {
            // Duas requisicoes criaram a mesma chave ao mesmo tempo: a segunda tenta de novo, agora encontrando a linha.
            tx.executeWithoutResult(s -> doHit(key, max));
        }
    }

    /** Conta a tentativa e ja lanca 429 se passou do limite: para acoes sensiveis (cadastro, esqueci a senha...). */
    public void consume(String key, int max) {
        hit(key, max);
        assertNotBlocked(key);
    }

    public void reset(String key) {
        tx.executeWithoutResult(s -> repo.apagarPorChave(key));
    }

    public int maxLoginPerEmail() {
        return cfg.maxLoginPerEmail();
    }

    public int maxLoginPerIp() {
        return cfg.maxLoginPerIp();
    }

    public int maxSensitivePerIp() {
        return cfg.maxSensitivePerIp();
    }

    private void doHit(String key, int max) {
        Instant now = Instant.now();
        LoginTentativa t = repo.buscarParaAtualizar(key).orElseGet(() -> repo.saveAndFlush(new LoginTentativa(key, now)));
        if (t.bloqueadoEm(now)) {
            return;
        }
        if (t.getJanelaInicio().plus(Duration.ofMinutes(cfg.windowMinutes())).isBefore(now)) {
            t.reiniciarJanela(now);
        }
        t.incrementar();
        if (t.getTentativas() >= max) {
            t.bloquearAte(now.plus(Duration.ofMinutes(cfg.lockMinutes())));
        }
    }
}
