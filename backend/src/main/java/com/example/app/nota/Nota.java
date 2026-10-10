package com.example.app.nota;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import com.example.app.tenant.TenantScopedEntity;

import java.time.Instant;

/**
 * MODULO DE EXEMPLO de entidade isolada por tenant. Copie este padrao nas suas entidades de negocio:
 * estender TenantScopedEntity (e o repositorio estender TenantScopedRepository) faz o Hibernate filtrar a
 * leitura/alteracao pelo tenant do token e preencher o tenant_id no INSERT. Nao ha como esquecer o filtro. Apague este pacote ao criar o produto real.
 */
@Entity
@Table(name = "nota")
public class Nota extends TenantScopedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String titulo;

    @Column(name = "criado_por", nullable = false, updatable = false)
    private Long criadoPor;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm;

    protected Nota() {
    }

    public Nota(String titulo, Long criadoPor) {
        this.titulo = titulo;
        this.criadoPor = criadoPor;
    }

    @PrePersist
    void aoCriar() {
        this.criadoEm = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getTitulo() {
        return titulo;
    }

    public Long getCriadoPor() {
        return criadoPor;
    }

    public Instant getCriadoEm() {
        return criadoEm;
    }
}
