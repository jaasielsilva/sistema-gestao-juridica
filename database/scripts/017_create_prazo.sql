-- 017_create_prazo.sql
-- Prazos processuais: varios por processo, com tipo do catalogo (enum no codigo), vencimento, responsavel e baixa.
-- A situacao exibida (HOJE / PROXIMO / VENCIDO) e calculada na leitura a partir de status + data_vencimento.
-- processo.proximo_prazo continua existindo: o servico o recalcula (menor vencimento ABERTO) a cada mudanca de prazo.
-- Rollback: DROP TABLE dbo.prazo;

CREATE TABLE dbo.prazo (
    id               BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT pk_prazo PRIMARY KEY,
    tenant_id        BIGINT               NOT NULL CONSTRAINT fk_prazo_tenant REFERENCES dbo.tenant (id),
    processo_id      BIGINT               NOT NULL CONSTRAINT fk_prazo_processo REFERENCES dbo.processo (id),
    tipo             VARCHAR(50)          NOT NULL,
    descricao        NVARCHAR(300)        NULL,
    data_vencimento  DATE                 NOT NULL,
    status           VARCHAR(10)          NOT NULL CONSTRAINT ck_prazo_status CHECK (status IN ('ABERTO', 'CUMPRIDO')),
    responsavel_id   BIGINT               NULL CONSTRAINT fk_prazo_responsavel REFERENCES dbo.usuario (id),
    cumprido_em      DATETIME2(3)         NULL,
    criado_em        DATETIME2(3)         NOT NULL CONSTRAINT df_prazo_criado DEFAULT SYSUTCDATETIME(),
    atualizado_em    DATETIME2(3)         NOT NULL CONSTRAINT df_prazo_atualizado DEFAULT SYSUTCDATETIME()
);
CREATE INDEX ix_prazo_tenant_status_venc ON dbo.prazo (tenant_id, status, data_vencimento, id);
CREATE INDEX ix_prazo_tenant_processo ON dbo.prazo (tenant_id, processo_id, status, data_vencimento);
CREATE INDEX ix_prazo_tenant_responsavel ON dbo.prazo (tenant_id, responsavel_id, status, data_vencimento);
