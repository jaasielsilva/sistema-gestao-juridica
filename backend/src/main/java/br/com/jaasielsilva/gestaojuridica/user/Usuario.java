package br.com.jaasielsilva.gestaojuridica.user;

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

@Entity
@Table(name = "usuario")
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Coluna comum (nao @TenantId): o login busca o usuario por e-mail ANTES de conhecer o tenant.
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private Long tenantId;

    @Column(nullable = false, length = 150)
    private String nome;

    @Column(nullable = false, length = 255, unique = true)
    private String email;

    @Column(name = "senha_hash", nullable = false, length = 100)
    private String senhaHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Papel papel = Papel.USER;

    @Column(nullable = false)
    private boolean ativo = true;

    @Column(name = "email_verificado", nullable = false)
    private boolean emailVerificado = false;

    @Column(name = "mfa_ativo", nullable = false)
    private boolean mfaAtivo = false;

    /** Segredo TOTP criptografado (AES-GCM, base64). Nulo se MFA nunca foi configurado. */
    @Column(name = "mfa_segredo", length = 255)
    private String mfaSegredo;

    /** Ultimo passo de tempo TOTP aceito: impede reutilizar o mesmo codigo (replay). */
    @Column(name = "mfa_ultimo_passo")
    private Long mfaUltimoPasso;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private Instant atualizadoEm;

    protected Usuario() {
    }

    public Usuario(Long tenantId, String nome, String email, String senhaHash, Papel papel) {
        this.tenantId = tenantId;
        this.nome = nome;
        this.email = email;
        this.senhaHash = senhaHash;
        this.papel = papel;
    }

    @PrePersist
    void aoCriar() {
        Instant agora = Instant.now();
        this.criadoEm = agora;
        this.atualizadoEm = agora;
    }

    @PreUpdate
    void aoAtualizar() {
        this.atualizadoEm = Instant.now();
    }

    public void definirSenhaHash(String novoHash) {
        this.senhaHash = novoHash;
    }

    public void marcarEmailVerificado() {
        this.emailVerificado = true;
    }

    public void iniciarConfiguracaoMfa(String segredoCriptografado) {
        this.mfaSegredo = segredoCriptografado;
        this.mfaAtivo = false;
        this.mfaUltimoPasso = null;
    }

    public void ativarMfa(long passo) {
        this.mfaAtivo = true;
        this.mfaUltimoPasso = passo;
    }

    public void desativarMfa() {
        this.mfaAtivo = false;
        this.mfaSegredo = null;
        this.mfaUltimoPasso = null;
    }

    public void registrarPassoMfa(long passo) {
        this.mfaUltimoPasso = passo;
    }

    public Long getId() {
        return id;
    }

    public Long getTenantId() {
        return tenantId;
    }

    public String getNome() {
        return nome;
    }

    public String getEmail() {
        return email;
    }

    public String getSenhaHash() {
        return senhaHash;
    }

    public Papel getPapel() {
        return papel;
    }

    public boolean isAtivo() {
        return ativo;
    }

    public boolean isEmailVerificado() {
        return emailVerificado;
    }

    public boolean isMfaAtivo() {
        return mfaAtivo;
    }

    public String getMfaSegredo() {
        return mfaSegredo;
    }

    public Long getMfaUltimoPasso() {
        return mfaUltimoPasso;
    }

    public Instant getCriadoEm() {
        return criadoEm;
    }

    public Instant getAtualizadoEm() {
        return atualizadoEm;
    }
}
