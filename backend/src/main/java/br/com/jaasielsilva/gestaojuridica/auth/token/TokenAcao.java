package br.com.jaasielsilva.gestaojuridica.auth.token;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "token_acao")
public class TokenAcao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "usuario_id", nullable = false, updatable = false)
    private Long usuarioId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 30)
    private TipoToken tipo;

    @Column(name = "token_hash", nullable = false, updatable = false, length = 64, columnDefinition = "char(64)")
    private String tokenHash;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm;

    @Column(name = "expira_em", nullable = false, updatable = false)
    private Instant expiraEm;

    @Column(name = "usado_em")
    private Instant usadoEm;

    protected TokenAcao() {
    }

    public TokenAcao(Long usuarioId, TipoToken tipo, String tokenHash, Instant expiraEm) {
        this.usuarioId = usuarioId;
        this.tipo = tipo;
        this.tokenHash = tokenHash;
        this.criadoEm = Instant.now();
        this.expiraEm = expiraEm;
    }

    public Long getUsuarioId() {
        return usuarioId;
    }
}
