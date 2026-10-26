-- 014_processo_indices_desempenho.sql
-- Desempenho da listagem de processos com centenas de milhares de registros (teste de carga k6, 200 mil processos).
-- A listagem ordena por (proximo_prazo, id); os indices passam a seguir essa ordem depois do filtro, entao a primeira
-- pagina sai do indice sem ordenar o conjunto todo. ix_processo_tenant_situacao tambem atende a contagem por situacao
-- do resumo (GROUP BY situacao).
-- Substitui: ix_processo_tenant_prazo (agora com id), ix_processo_tenant_situacao e ix_processo_tenant_cliente
-- (agora com proximo_prazo e id). Novo: ix_processo_tenant_tipo.
-- Rollback: DROP INDEX ix_processo_tenant_prazo ON dbo.processo; DROP INDEX ix_processo_tenant_situacao ON dbo.processo;
--           DROP INDEX ix_processo_tenant_cliente ON dbo.processo; DROP INDEX ix_processo_tenant_tipo ON dbo.processo;
--           CREATE INDEX ix_processo_tenant_cliente ON dbo.processo (tenant_id, cliente_id);
--           CREATE INDEX ix_processo_tenant_situacao ON dbo.processo (tenant_id, situacao);
--           CREATE INDEX ix_processo_tenant_prazo ON dbo.processo (tenant_id, proximo_prazo);

DROP INDEX ix_processo_tenant_prazo ON dbo.processo;
DROP INDEX ix_processo_tenant_situacao ON dbo.processo;
DROP INDEX ix_processo_tenant_cliente ON dbo.processo;

CREATE INDEX ix_processo_tenant_prazo ON dbo.processo (tenant_id, proximo_prazo, id);
CREATE INDEX ix_processo_tenant_situacao ON dbo.processo (tenant_id, situacao, proximo_prazo, id);
CREATE INDEX ix_processo_tenant_tipo ON dbo.processo (tenant_id, tipo, proximo_prazo, id);
CREATE INDEX ix_processo_tenant_cliente ON dbo.processo (tenant_id, cliente_id, proximo_prazo, id);
