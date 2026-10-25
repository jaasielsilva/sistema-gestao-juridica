-- limpar-dados-k6.sql (SOMENTE TESTE DE CARGA)
-- Remove o que o teste k6 criou: processos e clientes com observacoes = 'CARGA_K6' e os usuarios carga*@carga.teste.
-- NAO toca nos dados de carga_clientes.sql / carga_processos.sql (observacoes = 'CARGA_TESTE').
-- Uso: sqlcmd ... -v tenant=2 -i limpar-dados-k6.sql

SET NOCOUNT ON;
DECLARE @tenant BIGINT = $(tenant);

DELETE p FROM dbo.processo p WHERE p.tenant_id = @tenant AND p.observacoes = 'CARGA_K6';
DELETE c FROM dbo.cliente c WHERE c.tenant_id = @tenant AND c.observacoes = 'CARGA_K6'
    AND NOT EXISTS (SELECT 1 FROM dbo.processo p WHERE p.cliente_id = c.id);
-- Sessoes/tokens dos usuarios de carga precisam sair antes: veja README (apague pelas tabelas de sessao se existirem).
SELECT (SELECT COUNT(*) FROM dbo.cliente WHERE tenant_id = @tenant AND observacoes = 'CARGA_K6') AS clientes_k6_restantes,
       (SELECT COUNT(*) FROM dbo.processo WHERE tenant_id = @tenant AND observacoes = 'CARGA_K6') AS processos_k6_restantes;
