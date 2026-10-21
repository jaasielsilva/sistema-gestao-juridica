package br.com.jaasielsilva.gestaojuridica.mail;

import br.com.jaasielsilva.gestaojuridica.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * E-mails de conta. Metodos @Async: o envio nao atrasa a resposta (e o tempo de resposta nao revela se o
 * e-mail existe). Erros de envio sao logados, nunca propagados ao usuario.
 */
@Component
public class AccountMailer {

    private static final Logger log = LoggerFactory.getLogger(AccountMailer.class);

    private final EmailSender sender;
    private final String baseUrl;

    public AccountMailer(EmailSender sender, AppProperties props) {
        this.sender = sender;
        this.baseUrl = props.baseUrl().replaceAll("/+$", "");
    }

    @Async
    public void sendVerification(String to, String nome, String token) {
        safe(to, "Confirme seu e-mail",
                "Ola, " + nome + "!\n\nConfirme seu e-mail para ativar sua conta (o link vale por 24 horas):\n"
                        + link("/verificar-email", token)
                        + "\n\nSe voce nao criou esta conta, ignore esta mensagem.");
    }

    @Async
    public void sendPasswordReset(String to, String nome, String token) {
        safe(to, "Redefinicao de senha",
                "Ola, " + nome + ".\n\nRecebemos um pedido para redefinir sua senha. Use o link abaixo (vale por 1 hora, uso unico):\n"
                        + link("/redefinir-senha", token)
                        + "\n\nSe voce nao pediu isso, ignore esta mensagem: sua senha continua a mesma.");
    }

    private String link(String path, String token) {
        return baseUrl + path + "?token=" + URLEncoder.encode(token, StandardCharsets.UTF_8);
    }

    private void safe(String to, String subject, String body) {
        try {
            sender.send(to, subject, body);
        } catch (RuntimeException ex) {
            log.error("Falha ao enviar e-mail '{}'", subject, ex);
        }
    }
}
