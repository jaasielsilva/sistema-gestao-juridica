package br.com.jaasielsilva.gestaojuridica.auth.mfa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "mfa_recovery_code")
public class MfaRecoveryCode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "usuario_id", nullable = false, updatable = false)
    private Long usuarioId;

    @Column(name = "codigo_hash", nullable = false, updatable = false, length = 64, columnDefinition = "char(64)")
    private String codigoHash;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm;

    @Column(name = "usado_em")
    private Instant usadoEm;

    protected MfaRecoveryCode() {
    }

    public MfaRecoveryCode(Long usuarioId, String codigoHash) {
        this.usuarioId = usuarioId;
        this.codigoHash = codigoHash;
        this.criadoEm = Instant.now();
    }
}
