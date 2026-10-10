package com.example.app.mail;

/** Envio de e-mail transacional. Implementacao SMTP quando SPRING_MAIL_HOST esta definido; senao so registra em log. */
public interface EmailSender {

    void send(String to, String subject, String body);
}
