package br.com.jaasielsilva.gestaojuridica.tarefa;

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

/** Tarefa do quadro Kanban. Isolada por tenant (ver TenantScopedEntity). */
@Entity
@Table(name = "tarefa")
public class Tarefa extends TenantScopedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String titulo;

    @Column(length = 2000)
    private String descricao;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private StatusTarefa status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private PrioridadeTarefa prioridade;

    private LocalDate prazo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "processo_id")
    private Processo processo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "responsavel_id")
    private Usuario responsavel;

    /** Quem criou (vem do token); so essa pessoa ou um ADMIN apaga a tarefa. */
    @Column(name = "criado_por_id", nullable = false, updatable = false)
    private Long criadoPorId;

    @Column(nullable = false)
    private int ordem;

    @Column(name = "concluida_em")
    private Instant concluidaEm;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private Instant atualizadoEm;

    protected Tarefa() {
    }

    public Tarefa(String titulo, String descricao, StatusTarefa status, PrioridadeTarefa prioridade, LocalDate prazo,
                  Processo processo, Usuario responsavel, Long criadoPorId, int ordem, Instant agora) {
        this.titulo = titulo;
        this.descricao = descricao;
        this.status = status;
        this.prioridade = prioridade;
        this.prazo = prazo;
        this.processo = processo;
        this.responsavel = responsavel;
        this.criadoPorId = criadoPorId;
        this.ordem = ordem;
        this.concluidaEm = status == StatusTarefa.CONCLUIDA ? agora : null;
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

    public void atualizar(String titulo, String descricao, PrioridadeTarefa prioridade, LocalDate prazo,
                          Processo processo, Usuario responsavel) {
        this.titulo = titulo;
        this.descricao = descricao;
        this.prioridade = prioridade;
        this.prazo = prazo;
        this.processo = processo;
        this.responsavel = responsavel;
    }

    /** Troca de coluna; entrar em CONCLUIDA registra a data, sair dela limpa. */
    public void moverPara(StatusTarefa novo, Instant agora) {
        if (novo == this.status) {
            return;
        }
        this.status = novo;
        this.concluidaEm = novo == StatusTarefa.CONCLUIDA ? agora : null;
    }

    public void definirOrdem(int ordem) {
        this.ordem = ordem;
    }

    public Long getId() {
        return id;
    }

    public String getTitulo() {
        return titulo;
    }

    public String getDescricao() {
        return descricao;
    }

    public StatusTarefa getStatus() {
        return status;
    }

    public PrioridadeTarefa getPrioridade() {
        return prioridade;
    }

    public LocalDate getPrazo() {
        return prazo;
    }

    public Processo getProcesso() {
        return processo;
    }

    public Usuario getResponsavel() {
        return responsavel;
    }

    public Long getCriadoPorId() {
        return criadoPorId;
    }

    public int getOrdem() {
        return ordem;
    }

    public Instant getConcluidaEm() {
        return concluidaEm;
    }

    public Instant getCriadoEm() {
        return criadoEm;
    }
}
