package br.com.jaasielsilva.gestaojuridica.cliente;

import br.com.jaasielsilva.gestaojuridica.tenant.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;

/** Cliente do escritorio. Isolado por tenant (ver TenantScopedEntity). */
@Entity
@Table(name = "cliente")
public class Cliente extends TenantScopedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 2)
    private TipoPessoa tipo;

    @Column(nullable = false, length = 200)
    private String nome;

    /** Somente digitos do CPF/CNPJ. */
    @Column(nullable = false, length = 14)
    private String documento;

    @Column(length = 200)
    private String email;

    @Column(length = 20)
    private String telefone;

    @Column(length = 8)
    private String cep;

    @Column(length = 200)
    private String logradouro;

    @Column(length = 20)
    private String numero;

    @Column(length = 100)
    private String complemento;

    @Column(length = 100)
    private String bairro;

    @Column(length = 100)
    private String cidade;

    @Column(columnDefinition = "char(2)", length = 2)
    private String uf;

    @Column(length = 2000)
    private String observacoes;

    @Column(nullable = false)
    private boolean ativo = true;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private Instant atualizadoEm;

    protected Cliente() {
    }

    public Cliente(TipoPessoa tipo, String nome, String documento) {
        this.tipo = tipo;
        this.nome = nome;
        this.documento = documento;
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

    /** Substitui os dados cadastrais (o status ativo/inativo tem metodo proprio). */
    public void atualizar(TipoPessoa tipo, String nome, String documento, String email, String telefone, String cep,
                          String logradouro, String numero, String complemento, String bairro, String cidade,
                          String uf, String observacoes) {
        this.tipo = tipo;
        this.nome = nome;
        this.documento = documento;
        this.email = email;
        this.telefone = telefone;
        this.cep = cep;
        this.logradouro = logradouro;
        this.numero = numero;
        this.complemento = complemento;
        this.bairro = bairro;
        this.cidade = cidade;
        this.uf = uf;
        this.observacoes = observacoes;
    }

    public void definirAtivo(boolean ativo) {
        this.ativo = ativo;
    }

    public Long getId() {
        return id;
    }

    public TipoPessoa getTipo() {
        return tipo;
    }

    public String getNome() {
        return nome;
    }

    public String getDocumento() {
        return documento;
    }

    public String getEmail() {
        return email;
    }

    public String getTelefone() {
        return telefone;
    }

    public String getCep() {
        return cep;
    }

    public String getLogradouro() {
        return logradouro;
    }

    public String getNumero() {
        return numero;
    }

    public String getComplemento() {
        return complemento;
    }

    public String getBairro() {
        return bairro;
    }

    public String getCidade() {
        return cidade;
    }

    public String getUf() {
        return uf;
    }

    public String getObservacoes() {
        return observacoes;
    }

    public boolean isAtivo() {
        return ativo;
    }

    public Instant getCriadoEm() {
        return criadoEm;
    }

    public Instant getAtualizadoEm() {
        return atualizadoEm;
    }
}
