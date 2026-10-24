package br.com.jaasielsilva.gestaojuridica.processo;

import br.com.jaasielsilva.gestaojuridica.cliente.Cliente;
import br.com.jaasielsilva.gestaojuridica.tenant.TenantScopedEntity;
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

/** Processo judicial do escritorio. Isolado por tenant (ver TenantScopedEntity). */
@Entity
@Table(name = "processo")
public class Processo extends TenantScopedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cliente_id", nullable = false)
    private Cliente cliente;

    /** Somente os 20 digitos do numero CNJ. */
    @Column(nullable = false, length = 20)
    private String numero;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TipoProcesso tipo;

    @Column(name = "area_juridica", nullable = false, length = 100)
    private String areaJuridica;

    @Column(nullable = false, length = 150)
    private String tribunal;

    @Column(length = 150)
    private String comarca;

    @Column(length = 150)
    private String vara;

    @Column(name = "advogado_nome", nullable = false, length = 200)
    private String advogadoNome;

    @Column(name = "advogado_oab", length = 30)
    private String advogadoOab;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SituacaoProcesso situacao;

    @Column(name = "proximo_prazo")
    private LocalDate proximoPrazo;

    @Column(name = "proximo_prazo_descricao", length = 200)
    private String proximoPrazoDescricao;

    @Column(length = 2000)
    private String observacoes;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private Instant atualizadoEm;

    protected Processo() {
    }

    public Processo(Cliente cliente, String numero, TipoProcesso tipo, String areaJuridica, String tribunal,
                    String advogadoNome, SituacaoProcesso situacao) {
        this.cliente = cliente;
        this.numero = numero;
        this.tipo = tipo;
        this.areaJuridica = areaJuridica;
        this.tribunal = tribunal;
        this.advogadoNome = advogadoNome;
        this.situacao = situacao;
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

    public void atualizar(Cliente cliente, String numero, TipoProcesso tipo, String areaJuridica, String tribunal,
                          String comarca, String vara, String advogadoNome, String advogadoOab,
                          SituacaoProcesso situacao, LocalDate proximoPrazo, String proximoPrazoDescricao,
                          String observacoes) {
        this.cliente = cliente;
        this.numero = numero;
        this.tipo = tipo;
        this.areaJuridica = areaJuridica;
        this.tribunal = tribunal;
        this.comarca = comarca;
        this.vara = vara;
        this.advogadoNome = advogadoNome;
        this.advogadoOab = advogadoOab;
        this.situacao = situacao;
        this.proximoPrazo = proximoPrazo;
        this.proximoPrazoDescricao = proximoPrazoDescricao;
        this.observacoes = observacoes;
    }

    public Long getId() {
        return id;
    }

    public Cliente getCliente() {
        return cliente;
    }

    public String getNumero() {
        return numero;
    }

    public TipoProcesso getTipo() {
        return tipo;
    }

    public String getAreaJuridica() {
        return areaJuridica;
    }

    public String getTribunal() {
        return tribunal;
    }

    public String getComarca() {
        return comarca;
    }

    public String getVara() {
        return vara;
    }

    public String getAdvogadoNome() {
        return advogadoNome;
    }

    public String getAdvogadoOab() {
        return advogadoOab;
    }

    public SituacaoProcesso getSituacao() {
        return situacao;
    }

    public LocalDate getProximoPrazo() {
        return proximoPrazo;
    }

    public String getProximoPrazoDescricao() {
        return proximoPrazoDescricao;
    }

    public String getObservacoes() {
        return observacoes;
    }

    public Instant getCriadoEm() {
        return criadoEm;
    }

    public Instant getAtualizadoEm() {
        return atualizadoEm;
    }
}
