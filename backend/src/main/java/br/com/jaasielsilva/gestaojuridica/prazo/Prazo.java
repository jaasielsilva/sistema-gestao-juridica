package br.com.jaasielsilva.gestaojuridica.prazo;

import br.com.jaasielsilva.gestaojuridica.processo.Processo;
import br.com.jaasielsilva.gestaojuridica.tenant.TenantScopedEntity;
import br.com.jaasielsilva.gestaojuridica.user.Usuario;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;

/** Prazo processual de um processo. Isolado por tenant (ver TenantScopedEntity). */
@Entity
@Table(name = "prazo")
public class Prazo extends TenantScopedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "processo_id", nullable = false)
    private Processo processo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private TipoPrazo tipo;

    @Column(length = 300)
    private String descricao;

    @Column(name = "data_vencimento", nullable = false)
    private LocalDate dataVencimento;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private StatusPrazo status = StatusPrazo.ABERTO;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "responsavel_id")
    private Usuario responsavel;

    @Column(name = "cumprido_em")
    private Instant cumpridoEm;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private Instant atualizadoEm;

    protected Prazo() {
    }

    public Prazo(Processo processo, TipoPrazo tipo, String descricao, LocalDate dataVencimento, Usuario responsavel) {
        this.processo = processo;
        this.tipo = tipo;
        this.descricao = descricao;
        this.dataVencimento = dataVencimento;
        this.responsavel = responsavel;
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

    public void atualizar(TipoPrazo tipo, String descricao, LocalDate dataVencimento, Usuario responsavel) {
        this.tipo = tipo;
        this.descricao = descricao;
        this.dataVencimento = dataVencimento;
        this.responsavel = responsavel;
    }

    public void cumprir(Instant quando) {
        this.status = StatusPrazo.CUMPRIDO;
        this.cumpridoEm = quando;
    }

    public void reabrir() {
        this.status = StatusPrazo.ABERTO;
        this.cumpridoEm = null;
    }

    public Long getId() {
        return id;
    }

    public Processo getProcesso() {
        return processo;
    }

    public TipoPrazo getTipo() {
        return tipo;
    }

    public String getDescricao() {
        return descricao;
    }

    public LocalDate getDataVencimento() {
        return dataVencimento;
    }

    public StatusPrazo getStatus() {
        return status;
    }

    public Usuario getResponsavel() {
        return responsavel;
    }

    public Instant getCumpridoEm() {
        return cumpridoEm;
    }

    public Instant getCriadoEm() {
        return criadoEm;
    }

    public Instant getAtualizadoEm() {
        return atualizadoEm;
    }
}
