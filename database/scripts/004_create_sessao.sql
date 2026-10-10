-- 004_create_sessao.sql
-- Sessao de login (uma por dispositivo/login) e refresh tokens com rotacao.
-- Revogar a sessao invalida na hora o access token e todos os refresh tokens dela.
-- O refresh token em si NUNCA e guardado: so o hash SHA-256.
-- Rollback: DROP TABLE dbo.refresh_token; DROP TABLE dbo.sessao;

CREATE TABLE dbo.sessao (
    id          UNIQUEIDENTIFIER NOT NULL CONSTRAINT pk_sessao PRIMARY KEY,
    usuario_id  BIGINT           NOT NULL CONSTRAINT fk_sessao_usuario REFERENCES dbo.usuario (id),
    criado_em   DATETIME2(3)     NOT NULL CONSTRAINT df_sessao_criado DEFAULT SYSUTCDATETIME(),
    expira_em   DATETIME2(3)     NOT NULL,
    revogada_em DATETIME2(3)     NULL,
    ip          VARCHAR(45)      NULL,
    user_agent  NVARCHAR(300)    NULL
);
CREATE INDEX ix_sessao_usuario ON dbo.sessao (usuario_id);

CREATE TABLE dbo.refresh_token (
    id         BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT pk_refresh_token PRIMARY KEY,
    sessao_id  UNIQUEIDENTIFIER     NOT NULL CONSTRAINT fk_refresh_sessao REFERENCES dbo.sessao (id),
    token_hash CHAR(64)             NOT NULL,
    criado_em  DATETIME2(3)         NOT NULL CONSTRAINT df_refresh_criado DEFAULT SYSUTCDATETIME(),
    expira_em  DATETIME2(3)         NOT NULL,
    usado_em   DATETIME2(3)         NULL,
    CONSTRAINT uq_refresh_token_hash UNIQUE (token_hash)
);
CREATE INDEX ix_refresh_sessao ON dbo.refresh_token (sessao_id);
