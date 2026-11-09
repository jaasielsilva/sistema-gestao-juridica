package br.com.jaasielsilva.gestaojuridica.integracao.escavador;

import br.com.jaasielsilva.gestaojuridica.processo.Processo;
import br.com.jaasielsilva.gestaojuridica.tenant.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;

/** Movimentacao processual recebida via callback do Escavador, vinculada a um Processo existente. */
@Entity
@Table(name = "movimentacao")
public class Movimentacao extends TenantScopedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "processo_id", nullable = false)
    private Processo processo;

    @Column(name = "data_movimento", nullable = false)
    private Instant dataMovimento;

    @Column(name = "tipo_evento", nullable = false, length = 40)
    private String tipoEvento;

    @Column(nullable = false, length = 2000)
    private String descricao;

    @Column(nullable = false, length = 20)
    private String origem = "ESCAVADOR";

    /** Hash do payload do evento (sha256 hex, 64 chars) — dedup contra reentrega de callback. */
    @Column(name = "hash_dedup", nullable = false, length = 64)
    private String hashDedup;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm;

    protected Movimentacao() {
    }

    public Movimentacao(Processo processo, Instant dataMovimento, String tipoEvento, String descricao,
                        String hashDedup) {
        this.processo = processo;
        this.dataMovimento = dataMovimento;
        this.tipoEvento = tipoEvento;
        this.descricao = descricao;
        this.hashDedup = hashDedup;
    }

    @PrePersist
    void aoCriar() {
        this.criadoEm = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Processo getProcesso() {
        return processo;
    }

    public Instant getDataMovimento() {
        return dataMovimento;
    }

    public String getTipoEvento() {
        return tipoEvento;
    }

    public String getDescricao() {
        return descricao;
    }

    public String getOrigem() {
        return origem;
    }

    public String getHashDedup() {
        return hashDedup;
    }

    public Instant getCriadoEm() {
        return criadoEm;
    }
}
