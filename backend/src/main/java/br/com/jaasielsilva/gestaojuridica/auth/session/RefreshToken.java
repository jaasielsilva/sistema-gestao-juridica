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
@Table(name = "refresh_token")
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sessao_id", nullable = false, updatable = false)
    private UUID sessaoId;

    @Column(name = "token_hash", nullable = false, updatable = false, length = 64, columnDefinition = "char(64)")
    private String tokenHash;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm;

    @Column(name = "expira_em", nullable = false, updatable = false)
    private Instant expiraEm;

    @Column(name = "usado_em")
    private Instant usadoEm;

    protected RefreshToken() {
    }

    public RefreshToken(UUID sessaoId, String tokenHash, Instant expiraEm) {
        this.sessaoId = sessaoId;
        this.tokenHash = tokenHash;
        this.criadoEm = Instant.now();
        this.expiraEm = expiraEm;
    }

    public void marcarUsado(Instant agora) {
        this.usadoEm = agora;
    }

    public UUID getSessaoId() {
        return sessaoId;
    }

    public Instant getExpiraEm() {
        return expiraEm;
    }

    public Instant getUsadoEm() {
        return usadoEm;
    }
}
