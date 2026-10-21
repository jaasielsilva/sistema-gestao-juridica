package br.com.jaasielsilva.gestaojuridica.shared;

import br.com.jaasielsilva.gestaojuridica.auth.session.RefreshTokenRepository;
import br.com.jaasielsilva.gestaojuridica.auth.session.SessaoRepository;
import br.com.jaasielsilva.gestaojuridica.auth.throttle.LoginTentativaRepository;
import br.com.jaasielsilva.gestaojuridica.auth.token.TokenAcaoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

/** Limpeza diaria de dados de seguranca ja expirados. NAO mexe na auditoria (retencao e decisao do produto). */
@Component
public class HousekeepingJob {

    private static final Logger log = LoggerFactory.getLogger(HousekeepingJob.class);

    private final RefreshTokenRepository refreshTokens;
    private final SessaoRepository sessoes;
    private final TokenAcaoRepository tokens;
    private final LoginTentativaRepository tentativas;

    public HousekeepingJob(RefreshTokenRepository refreshTokens, SessaoRepository sessoes,
                           TokenAcaoRepository tokens, LoginTentativaRepository tentativas) {
        this.refreshTokens = refreshTokens;
        this.sessoes = sessoes;
        this.tokens = tokens;
        this.tentativas = tentativas;
    }

    @Scheduled(cron = "0 30 3 * * *")
    @Transactional
    public void limpar() {
        Instant now = Instant.now();
        Instant limite = now.minus(Duration.ofDays(1));
        int rt = refreshTokens.apagarExpiradosAntesDe(limite);
        // Refresh tokens expirados saem antes; so entao as sessoes (FK refresh_token -> sessao).
        int ses = sessoes.apagarExpiradasAntesDe(now.minus(Duration.ofDays(30)));
        int tok = tokens.apagarExpiradosAntesDe(limite);
        int tent = tentativas.apagarAntigas(limite, now);
        log.info("Limpeza de seguranca: refresh_tokens={} sessoes={} tokens_acao={} tentativas={}", rt, ses, tok, tent);
    }
}
