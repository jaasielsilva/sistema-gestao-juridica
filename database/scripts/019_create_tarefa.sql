-- 019_create_tarefa.sql
-- Tarefas do escritorio em quadro Kanban (colunas = status), opcionalmente ligadas a um processo e a um responsavel.
-- ordem define a posicao do cartao dentro da coluna (0 = topo); o servico renumera a coluna ao mover.
-- Rollback: DROP TABLE dbo.tarefa;

CREATE TABLE dbo.tarefa (
    id              BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT pk_tarefa PRIMARY KEY,
    tenant_id       BIGINT               NOT NULL CONSTRAINT fk_tarefa_tenant REFERENCES dbo.tenant (id),
    titulo          NVARCHAR(150)        NOT NULL,
    descricao       NVARCHAR(2000)       NULL,
    status          VARCHAR(15)          NOT NULL CONSTRAINT ck_tarefa_status CHECK (status IN (
                    'A_FAZER', 'EM_ANDAMENTO', 'AGUARDANDO', 'CONCLUIDA')),
    prioridade      VARCHAR(10)          NOT NULL CONSTRAINT ck_tarefa_prioridade CHECK (prioridade IN (
                    'BAIXA', 'MEDIA', 'ALTA', 'URGENTE')),
    prazo           DATE                 NULL,
    processo_id     BIGINT               NULL CONSTRAINT fk_tarefa_processo REFERENCES dbo.processo (id),
    responsavel_id  BIGINT               NULL CONSTRAINT fk_tarefa_responsavel REFERENCES dbo.usuario (id),
    criado_por_id   BIGINT               NOT NULL CONSTRAINT fk_tarefa_criador REFERENCES dbo.usuario (id),
    ordem           INT                  NOT NULL,
    concluida_em    DATETIME2(3)         NULL,
    criado_em       DATETIME2(3)         NOT NULL CONSTRAINT df_tarefa_criado DEFAULT SYSUTCDATETIME(),
    atualizado_em   DATETIME2(3)         NOT NULL CONSTRAINT df_tarefa_atualizado DEFAULT SYSUTCDATETIME()
);
CREATE INDEX ix_tarefa_tenant_status_ordem ON dbo.tarefa (tenant_id, status, ordem, id);
CREATE INDEX ix_tarefa_tenant_responsavel ON dbo.tarefa (tenant_id, responsavel_id, status);
CREATE INDEX ix_tarefa_tenant_processo ON dbo.tarefa (tenant_id, processo_id);
