-- 010_create_cliente.sql
-- Cadastro de clientes do escritorio (pessoa fisica ou juridica), isolado por tenant.
-- documento guarda so os digitos do CPF (11) ou CNPJ (14); e unico por tenant.
-- Clientes nao sao apagados: ficam inativos (ativo = 0) para preservar o historico.
-- Rollback: DROP TABLE dbo.cliente;

CREATE TABLE dbo.cliente (
    id            BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT pk_cliente PRIMARY KEY,
    tenant_id     BIGINT               NOT NULL CONSTRAINT fk_cliente_tenant REFERENCES dbo.tenant (id),
    tipo          VARCHAR(2)           NOT NULL CONSTRAINT ck_cliente_tipo CHECK (tipo IN ('PF', 'PJ')),
    nome          NVARCHAR(200)        NOT NULL,
    documento     VARCHAR(14)          NOT NULL,
    email         NVARCHAR(200)        NULL,
    telefone      VARCHAR(20)          NULL,
    cep           VARCHAR(8)           NULL,
    logradouro    NVARCHAR(200)        NULL,
    numero        NVARCHAR(20)         NULL,
    complemento   NVARCHAR(100)        NULL,
    bairro        NVARCHAR(100)        NULL,
    cidade        NVARCHAR(100)        NULL,
    uf            CHAR(2)              NULL,
    observacoes   NVARCHAR(2000)       NULL,
    ativo         BIT                  NOT NULL CONSTRAINT df_cliente_ativo DEFAULT 1,
    criado_em     DATETIME2(3)         NOT NULL CONSTRAINT df_cliente_criado DEFAULT SYSUTCDATETIME(),
    atualizado_em DATETIME2(3)         NOT NULL CONSTRAINT df_cliente_atualizado DEFAULT SYSUTCDATETIME()
);
CREATE UNIQUE INDEX ux_cliente_tenant_documento ON dbo.cliente (tenant_id, documento);
CREATE INDEX ix_cliente_tenant_nome ON dbo.cliente (tenant_id, nome);
