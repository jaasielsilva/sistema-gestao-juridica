package br.com.jaasielsilva.gestaojuridica.integracao.escavador;

import br.com.jaasielsilva.gestaojuridica.tenant.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Credencial Escavador do tenant: token (PAT) e segredo de callback da PROPRIA conta Escavador do
 * escritorio, nunca compartilhados entre tenants. Um registro por tenant (ux_escavador_configuracao_tenant).
 */
@Entity
@Table(name = "escavador_configuracao")
public class EscavadorConfiguracao extends TenantScopedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "token_cifrado", nullable = false, length = 500)
    private String tokenCifrado;

    @Column(name = "callback_secret_cifrado", nullable = false, length = 500)
    private String callbackSecretCifrado;

    /** Identificador opaco usado na URL publica de callback — nunca o tenant_id sequencial. */
    @Column(name = "callback_slug", nullable = false)
    private UUID callbackSlug;

    @Column(nullable = false)
    private boolean habilitado = true;

    @Column(name = "saldo_limite_centavos", nullable = false)
    private long saldoLimiteCentavos;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private Instant atualizadoEm;

    protected EscavadorConfiguracao() {
    }

    public EscavadorConfiguracao(String tokenCifrado, String callbackSecretCifrado, long saldoLimiteCentavos) {
        this.tokenCifrado = tokenCifrado;
        this.callbackSecretCifrado = callbackSecretCifrado;
        this.callbackSlug = UUID.randomUUID();
        this.habilitado = true;
        this.saldoLimiteCentavos = saldoLimiteCentavos;
    }

    @PrePersist
    void aoCriar() {
        this.criadoEm = Instant.now();
        this.atualizadoEm = this.criadoEm;
    }

    @PreUpdate
    void aoAtualizar() {
        this.atualizadoEm = Instant.now();
    }

    public void atualizarCredenciais(String tokenCifrado, String callbackSecretCifrado) {
        this.tokenCifrado = tokenCifrado;
        this.callbackSecretCifrado = callbackSecretCifrado;
    }

    public void definirHabilitado(boolean habilitado) {
        this.habilitado = habilitado;
    }

    public void definirSaldoLimiteCentavos(long saldoLimiteCentavos) {
        this.saldoLimiteCentavos = saldoLimiteCentavos;
    }

    public Long getId() {
        return id;
    }

    public String getTokenCifrado() {
        return tokenCifrado;
    }

    public String getCallbackSecretCifrado() {
        return callbackSecretCifrado;
    }

    public UUID getCallbackSlug() {
        return callbackSlug;
    }

    public boolean isHabilitado() {
        return habilitado;
    }

    public long getSaldoLimiteCentavos() {
        return saldoLimiteCentavos;
    }

    public Instant getCriadoEm() {
        return criadoEm;
    }

    public Instant getAtualizadoEm() {
        return atualizadoEm;
    }
}
