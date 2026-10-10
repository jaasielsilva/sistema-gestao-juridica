-- 002_create_tenant.sql
-- Multi-tenancy: cada empresa/cliente do produto e um tenant. Todo usuario pertence a exatamente um tenant.
-- Usuarios existentes sao movidos para o tenant padrao (id 1).
-- Rollback: ALTER TABLE dbo.usuario DROP CONSTRAINT fk_usuario_tenant; DROP INDEX ix_usuario_tenant ON dbo.usuario;
--           ALTER TABLE dbo.usuario DROP COLUMN tenant_id; DROP TABLE dbo.tenant;

CREATE TABLE dbo.tenant (
    id        BIGINT        NOT NULL CONSTRAINT pk_tenant PRIMARY KEY,
    nome      NVARCHAR(150) NOT NULL,
    ativo     BIT           NOT NULL CONSTRAINT df_tenant_ativo DEFAULT 1,
    criado_em DATETIME2(3)  NOT NULL CONSTRAINT df_tenant_criado DEFAULT SYSUTCDATETIME(),
    CONSTRAINT uq_tenant_nome UNIQUE (nome)
);

INSERT INTO dbo.tenant (id, nome) VALUES (1, N'Padrao');

ALTER TABLE dbo.usuario ADD tenant_id BIGINT NOT NULL CONSTRAINT df_usuario_tenant DEFAULT 1;
ALTER TABLE dbo.usuario ADD CONSTRAINT fk_usuario_tenant FOREIGN KEY (tenant_id) REFERENCES dbo.tenant (id);
ALTER TABLE dbo.usuario DROP CONSTRAINT df_usuario_tenant;
CREATE INDEX ix_usuario_tenant ON dbo.usuario (tenant_id);
