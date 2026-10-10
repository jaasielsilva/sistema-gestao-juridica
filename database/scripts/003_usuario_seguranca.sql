-- 003_usuario_seguranca.sql
-- Verificacao de e-mail e MFA (TOTP) no usuario.
-- Usuarios que ja existiam sao considerados verificados (backfill); novos usuarios nascem NAO verificados.
-- O segredo TOTP e guardado CRIPTOGRAFADO pela aplicacao (AES-GCM); nunca em texto puro.
-- Rollback: remover as constraints default e as colunas email_verificado, mfa_ativo, mfa_segredo, mfa_ultimo_passo.

ALTER TABLE dbo.usuario ADD email_verificado BIT NOT NULL CONSTRAINT df_usuario_email_verificado DEFAULT 1;
ALTER TABLE dbo.usuario DROP CONSTRAINT df_usuario_email_verificado;
ALTER TABLE dbo.usuario ADD CONSTRAINT df_usuario_email_verificado DEFAULT 0 FOR email_verificado;

ALTER TABLE dbo.usuario ADD
    mfa_ativo        BIT           NOT NULL CONSTRAINT df_usuario_mfa_ativo DEFAULT 0,
    mfa_segredo      NVARCHAR(255) NULL,
    mfa_ultimo_passo BIGINT        NULL;
