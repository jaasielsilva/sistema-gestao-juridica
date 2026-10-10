-- 008_create_nota.sql
-- MODULO DE EXEMPLO (apague ao criar um produto real): entidade com isolamento por tenant.
-- Padrao para qualquer tabela de negocio: coluna tenant_id NOT NULL + FK + indice com tenant_id na frente.
-- Rollback: DROP TABLE dbo.nota;

CREATE TABLE dbo.nota (
    id         BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT pk_nota PRIMARY KEY,
    tenant_id  BIGINT               NOT NULL CONSTRAINT fk_nota_tenant REFERENCES dbo.tenant (id),
    titulo     NVARCHAR(200)        NOT NULL,
    criado_por BIGINT               NOT NULL,
    criado_em  DATETIME2(3)         NOT NULL CONSTRAINT df_nota_criado DEFAULT SYSUTCDATETIME()
);
CREATE INDEX ix_nota_tenant ON dbo.nota (tenant_id, criado_em DESC);
