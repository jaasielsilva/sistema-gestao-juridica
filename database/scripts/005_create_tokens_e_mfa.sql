-- 005_create_tokens_e_mfa.sql
-- token_acao: tokens de uso unico enviados por e-mail (verificacao de e-mail e redefinicao de senha). So o hash e guardado.
-- mfa_recovery_code: codigos de recuperacao do MFA (hash), cada um vale uma vez.
-- Rollback: DROP TABLE dbo.mfa_recovery_code; DROP TABLE dbo.token_acao;

CREATE TABLE dbo.token_acao (
    id         BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT pk_token_acao PRIMARY KEY,
    usuario_id BIGINT               NOT NULL CONSTRAINT fk_token_acao_usuario REFERENCES dbo.usuario (id),
    tipo       VARCHAR(30)          NOT NULL,
    token_hash CHAR(64)             NOT NULL,
    criado_em  DATETIME2(3)         NOT NULL CONSTRAINT df_token_acao_criado DEFAULT SYSUTCDATETIME(),
    expira_em  DATETIME2(3)         NOT NULL,
    usado_em   DATETIME2(3)         NULL,
    CONSTRAINT uq_token_acao_hash UNIQUE (token_hash),
    CONSTRAINT ck_token_acao_tipo CHECK (tipo IN ('VERIFICACAO_EMAIL', 'RESET_SENHA'))
);
CREATE INDEX ix_token_acao_usuario ON dbo.token_acao (usuario_id, tipo);

CREATE TABLE dbo.mfa_recovery_code (
    id          BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT pk_mfa_recovery_code PRIMARY KEY,
    usuario_id  BIGINT               NOT NULL CONSTRAINT fk_mfa_recovery_usuario REFERENCES dbo.usuario (id),
    codigo_hash CHAR(64)             NOT NULL,
    criado_em   DATETIME2(3)         NOT NULL CONSTRAINT df_mfa_recovery_criado DEFAULT SYSUTCDATETIME(),
    usado_em    DATETIME2(3)         NULL,
    CONSTRAINT uq_mfa_recovery UNIQUE (usuario_id, codigo_hash)
);
