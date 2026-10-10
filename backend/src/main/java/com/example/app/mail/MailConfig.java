package com.example.app.mail;

import com.example.app.config.AppProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;

/** Escolhe o EmailSender: SMTP se SPRING_MAIL_HOST estiver definido; senao o fallback que so registra em log. */
@Configuration
public class MailConfig {

    @Bean
    @ConditionalOnProperty("spring.mail.host")
    EmailSender smtpEmailSender(JavaMailSender mailSender, AppProperties props) {
        return new SmtpEmailSender(mailSender, props);
    }

    @Bean
    @ConditionalOnMissingBean(EmailSender.class)
    EmailSender loggingEmailSender(AppProperties props) {
        return new LoggingEmailSender(props);
    }
}
