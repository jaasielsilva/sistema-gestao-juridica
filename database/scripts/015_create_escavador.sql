-- 015_create_escavador.sql
-- Integracao com o Escavador (monitoramento de movimentacoes processuais). Cada tenant cadastra a
-- propria chave (PAT) e o proprio segredo de callback do Escavador; nada e compartilhado entre tenants.
-- Ver docs/adr/0007-integracao-escavador.md.
-- Rollback: ALTER TABLE dbo.processo DROP CONSTRAINT ck_processo_frequencia_monitoramento;
--           ALTER TABLE dbo.processo DROP COLUMN monitoramento_escavador_id, frequencia_monitoramento;
--           DROP TABLE dbo.movimentacao; DROP TABLE dbo.escavador_configuracao;

CREATE TABLE dbo.escavador_configuracao (
    id                          BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT pk_escavador_configuracao PRIMARY KEY,
    tenant_id                   BIGINT               NOT NULL CONSTRAINT fk_escavador_configuracao_tenant REFERENCES dbo.tenant (id),
    -- Token (PAT) e segredo de callback da conta Escavador do PROPRIO tenant, cifrados (AES-256-GCM, ver SecretCipher/EscavadorCipher).
    token_cifrado               NVARCHAR(500)        NOT NULL,
    callback_secret_cifrado     NVARCHAR(500)        NOT NULL,
    -- Identificador opaco (nao sequencial) usado na URL publica de callback: /api/v1/integracoes/escavador/callback/{callback_slug}.
    callback_slug               UNIQUEIDENTIFIER     NOT NULL,
    habilitado                  BIT                  NOT NULL CONSTRAINT df_escavador_configuracao_habilitado DEFAULT 1,
    -- Limite mensal de gasto com monitoramento, em centavos (configuravel por plano comercial).
    saldo_limite_centavos       BIGINT               NOT NULL,
    criado_em                   DATETIME2(3)         NOT NULL CONSTRAINT df_escavador_configuracao_criado DEFAULT SYSUTCDATETIME(),
    atualizado_em                DATETIME2(3)        NOT NULL CONSTRAINT df_escavador_configuracao_atualizado DEFAULT SYSUTCDATETIME()
);
CREATE UNIQUE INDEX ux_escavador_configuracao_tenant ON dbo.escavador_configuracao (tenant_id);
CREATE UNIQUE INDEX ux_escavador_configuracao_slug ON dbo.escavador_configuracao (callback_slug);

CREATE TABLE dbo.movimentacao (
    id              BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT pk_movimentacao PRIMARY KEY,
    tenant_id       BIGINT               NOT NULL CONSTRAINT fk_movimentacao_tenant REFERENCES dbo.tenant (id),
    processo_id     BIGINT               NOT NULL CONSTRAINT fk_movimentacao_processo REFERENCES dbo.processo (id),
    data_movimento  DATETIME2(3)         NOT NULL,
    tipo_evento     VARCHAR(40)          NOT NULL,
    descricao       NVARCHAR(2000)       NOT NULL,
    origem          VARCHAR(20)          NOT NULL CONSTRAINT df_movimentacao_origem DEFAULT 'ESCAVADOR',
    -- Hash do payload do evento: evita gravar a mesma movimentacao duas vezes se o Escavador reentregar o callback.
    hash_dedup      VARCHAR(64)          NOT NULL,
    criado_em       DATETIME2(3)         NOT NULL CONSTRAINT df_movimentacao_criado DEFAULT SYSUTCDATETIME()
);
CREATE UNIQUE INDEX ux_movimentacao_processo_hash ON dbo.movimentacao (processo_id, hash_dedup);
CREATE INDEX ix_movimentacao_tenant_processo ON dbo.movimentacao (tenant_id, processo_id, data_movimento);

ALTER TABLE dbo.processo ADD
    monitoramento_escavador_id   BIGINT       NULL,
    frequencia_monitoramento     VARCHAR(10)  NULL CONSTRAINT ck_processo_frequencia_monitoramento CHECK (
                                  frequencia_monitoramento IN ('DIARIA', 'SEMANAL', 'MENSAL'));
