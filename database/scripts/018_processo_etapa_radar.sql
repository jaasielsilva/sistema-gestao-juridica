-- 018_processo_etapa_radar.sql
-- Radar de processos: etapa atual do processo e quando entrou nela. Os "dias na etapa" saem de etapa_atualizada_em.
-- Processos existentes recebem a etapa ANALISE e a data de criacao como entrada na etapa.
-- O EXEC e necessario porque a coluna nova so existe depois do ALTER (o lote inteiro e compilado antes).
-- Rollback: DROP INDEX ix_processo_tenant_radar ON dbo.processo;
--           ALTER TABLE dbo.processo DROP CONSTRAINT df_processo_etapa_em, ck_processo_etapa, df_processo_etapa;
--           ALTER TABLE dbo.processo DROP COLUMN etapa, etapa_atualizada_em;

ALTER TABLE dbo.processo ADD
    etapa VARCHAR(25) NOT NULL CONSTRAINT df_processo_etapa DEFAULT 'ANALISE'
        CONSTRAINT ck_processo_etapa CHECK (etapa IN (
            'CONTATO', 'ANALISE', 'ELABORACAO_PECA', 'PROTOCOLADO', 'AGUARDANDO_DECISAO', 'RECURSO',
            'CUMPRIMENTO', 'CONCLUSAO')),
    etapa_atualizada_em DATETIME2(3) NULL;

EXEC('UPDATE dbo.processo SET etapa_atualizada_em = criado_em');

ALTER TABLE dbo.processo ALTER COLUMN etapa_atualizada_em DATETIME2(3) NOT NULL;
ALTER TABLE dbo.processo ADD CONSTRAINT df_processo_etapa_em DEFAULT SYSUTCDATETIME() FOR etapa_atualizada_em;

-- Radar: mais antigo primeiro, situacao no indice para filtrar encerrados sem ir a tabela.
CREATE INDEX ix_processo_tenant_radar ON dbo.processo (tenant_id, etapa_atualizada_em, id) INCLUDE (situacao);
