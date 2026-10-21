package br.com.jaasielsilva.gestaojuridica.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** Imutavel por design: sem setters. O usuario de runtime do banco tambem nao tem UPDATE/DELETE nesta tabela. */
@Entity
@Table(name = "evento_auditoria")
public class EventoAuditoria {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ocorrido_em", nullable = false, updatable = false)
    private Instant ocorridoEm;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 50)
    private TipoEvento tipo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 10)
    private Resultado resultado;

    @Column(name = "usuario_id", updatable = false)
    private Long usuarioId;

    @Column(name = "tenant_id", updatable = false)
    private Long tenantId;

    @Column(name = "email_informado", updatable = false, length = 255)
    private String emailInformado;

    @Column(updatable = false, length = 45)
    private String ip;

    @Column(name = "correlation_id", updatable = false, length = 64)
    private String correlationId;

    @Column(updatable = false, length = 500)
    private String detalhe;

    protected EventoAuditoria() {
    }

    public EventoAuditoria(TipoEvento tipo, Resultado resultado, Long usuarioId, Long tenantId,
                           String emailInformado, String ip, String correlationId, String detalhe) {
        this.ocorridoEm = Instant.now();
        this.tipo = tipo;
        this.resultado = resultado;
        this.usuarioId = usuarioId;
        this.tenantId = tenantId;
        this.emailInformado = emailInformado;
        this.ip = ip;
        this.correlationId = correlationId;
        this.detalhe = detalhe == null ? null : detalhe.substring(0, Math.min(500, detalhe.length()));
    }

    public Long getId() {
        return id;
    }

    public Instant getOcorridoEm() {
        return ocorridoEm;
    }

    public TipoEvento getTipo() {
        return tipo;
    }

    public Resultado getResultado() {
        return resultado;
    }

    public Long getUsuarioId() {
        return usuarioId;
    }

    public Long getTenantId() {
        return tenantId;
    }

    public String getEmailInformado() {
        return emailInformado;
    }

    public String getIp() {
        return ip;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public String getDetalhe() {
        return detalhe;
    }
}
