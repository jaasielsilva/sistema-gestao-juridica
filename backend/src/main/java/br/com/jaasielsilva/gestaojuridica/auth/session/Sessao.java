package br.com.jaasielsilva.gestaojuridica.auth.session;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "sessao")
public class Sessao {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "usuario_id", nullable = false, updatable = false)
    private Long usuarioId;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm;

    @Column(name = "expira_em", nullable = false)
    private Instant expiraEm;

    @Column(name = "revogada_em")
    private Instant revogadaEm;

    @Column(length = 45, updatable = false)
    private String ip;

    @Column(name = "user_agent", length = 300, updatable = false)
    private String userAgent;

    protected Sessao() {
    }

    public Sessao(Long usuarioId, Instant expiraEm, String ip, String userAgent) {
        this.usuarioId = usuarioId;
        this.criadoEm = Instant.now();
        this.expiraEm = expiraEm;
        this.ip = ip;
        this.userAgent = userAgent == null ? null : userAgent.substring(0, Math.min(300, userAgent.length()));
    }

    public boolean estaAtiva(Instant agora) {
        return revogadaEm == null && expiraEm.isAfter(agora);
    }

    public void revogar(Instant agora) {
        if (revogadaEm == null) {
            revogadaEm = agora;
        }
    }

    public void estenderAte(Instant novaExpiracao) {
        this.expiraEm = novaExpiracao;
    }

    public UUID getId() {
        return id;
    }

    public Long getUsuarioId() {
        return usuarioId;
    }

    public Instant getExpiraEm() {
        return expiraEm;
    }

    public Instant getRevogadaEm() {
        return revogadaEm;
    }
}
