package com.example.app.mail;

import com.example.app.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Fallback sem SMTP (desenvolvimento/testes). NAO envia nada. Por padrao registra so destinatario e assunto:
 * o corpo contem links com token de uso unico e nao deve ir para logs (MAIL_LOG_BODY=true so em dev).
 */
public class LoggingEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingEmailSender.class);

    private final boolean logBody;

    public LoggingEmailSender(AppProperties props) {
        this.logBody = props.mail().logBody();
    }

    @Override
    public void send(String to, String subject, String body) {
        if (logBody) {
            log.warn("[e-mail NAO enviado - sem SMTP] para={} assunto={}\n{}", to, subject, body);
        } else {
            log.warn("[e-mail NAO enviado - sem SMTP configurado] para={} assunto={}", to, subject);
        }
    }
}
