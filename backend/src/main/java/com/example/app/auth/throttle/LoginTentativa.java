package com.example.app.auth.throttle;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "login_tentativa")
public class LoginTentativa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200, updatable = false)
    private String chave;

    @Column(nullable = false)
    private int tentativas;

    @Column(name = "janela_inicio", nullable = false)
    private Instant janelaInicio;

    @Column(name = "bloqueado_ate")
    private Instant bloqueadoAte;

    protected LoginTentativa() {
    }

    public LoginTentativa(String chave, Instant agora) {
        this.chave = chave;
        this.janelaInicio = agora;
    }

    public int getTentativas() {
        return tentativas;
    }

    public Instant getJanelaInicio() {
        return janelaInicio;
    }

    public Instant getBloqueadoAte() {
        return bloqueadoAte;
    }

    public boolean bloqueadoEm(Instant agora) {
        return bloqueadoAte != null && bloqueadoAte.isAfter(agora);
    }

    public void reiniciarJanela(Instant agora) {
        this.tentativas = 0;
        this.janelaInicio = agora;
        this.bloqueadoAte = null;
    }

    public void incrementar() {
        this.tentativas++;
    }

    public void bloquearAte(Instant ate) {
        this.bloqueadoAte = ate;
    }
}
