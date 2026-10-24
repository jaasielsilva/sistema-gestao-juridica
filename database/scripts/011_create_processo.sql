-- 011_create_processo.sql
-- Processos judiciais do escritorio, isolados por tenant e vinculados a um cliente.
-- numero guarda so os 20 digitos do padrao CNJ (NNNNNNN-DD.AAAA.J.TR.OOOO); e unico por tenant.
-- Processos nao sao apagados: a situacao (incluindo ENCERRADO) preserva o historico.
-- Rollback: DROP TABLE dbo.processo;

CREATE TABLE dbo.processo (
    id                       BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT pk_processo PRIMARY KEY,
    tenant_id                BIGINT               NOT NULL CONSTRAINT fk_processo_tenant REFERENCES dbo.tenant (id),
    cliente_id               BIGINT               NOT NULL CONSTRAINT fk_processo_cliente REFERENCES dbo.cliente (id),
    numero                   VARCHAR(20)          NOT NULL,
    tipo                     VARCHAR(20)          NOT NULL CONSTRAINT ck_processo_tipo CHECK (tipo IN (
                             'CIVIL', 'TRABALHISTA', 'EMPRESARIAL', 'FAMILIA', 'CRIMINAL', 'ADMINISTRATIVO', 'BANCARIO')),
    area_juridica            NVARCHAR(100)        NOT NULL,
    tribunal                 NVARCHAR(150)        NOT NULL,
    comarca                  NVARCHAR(150)        NULL,
    vara                     NVARCHAR(150)        NULL,
    advogado_nome            NVARCHAR(200)        NOT NULL,
    advogado_oab             VARCHAR(30)          NULL,
    situacao                 VARCHAR(20)          NOT NULL CONSTRAINT ck_processo_situacao CHECK (situacao IN (
                             'EM_ANDAMENTO', 'PENDENTE', 'SUSPENSO', 'ENCERRADO', 'URGENTE')),
    proximo_prazo            DATE                 NULL,
    proximo_prazo_descricao  NVARCHAR(200)        NULL,
    observacoes              NVARCHAR(2000)       NULL,
    criado_em                DATETIME2(3)         NOT NULL CONSTRAINT df_processo_criado DEFAULT SYSUTCDATETIME(),
    atualizado_em            DATETIME2(3)         NOT NULL CONSTRAINT df_processo_atualizado DEFAULT SYSUTCDATETIME()
);
CREATE UNIQUE INDEX ux_processo_tenant_numero ON dbo.processo (tenant_id, numero);
CREATE INDEX ix_processo_tenant_cliente ON dbo.processo (tenant_id, cliente_id);
CREATE INDEX ix_processo_tenant_situacao ON dbo.processo (tenant_id, situacao);
CREATE INDEX ix_processo_tenant_prazo ON dbo.processo (tenant_id, proximo_prazo);
