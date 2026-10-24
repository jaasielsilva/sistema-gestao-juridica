-- carga_processos_limpar.sql (SOMENTE DESENVOLVIMENTO)
-- Remove os processos gerados por carga_processos.sql (observacoes = 'CARGA_TESTE') do tenant $(tenant).
-- Processos cadastrados de verdade nao sao tocados.
--
-- Uso:
--   MSYS_NO_PATHCONV=1 docker compose exec -T db bash -c '/opt/mssql-tools18/bin/sqlcmd -C -S localhost -U sa -P "$MSSQL_SA_PASSWORD" -d appdb -v tenant=2 -i /dev/stdin' < database/seed/carga_processos_limpar.sql

SET NOCOUNT ON;

DELETE FROM dbo.processo WHERE tenant_id = $(tenant) AND observacoes = N'CARGA_TESTE';
PRINT CONCAT('Processos de teste removidos: ', @@ROWCOUNT);
