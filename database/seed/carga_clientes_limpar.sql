-- carga_clientes_limpar.sql  (SOMENTE DESENVOLVIMENTO)
-- Remove os clientes gerados por carga_clientes.sql (observacoes = 'CARGA_TESTE') do tenant $(tenant).
-- Clientes cadastrados de verdade nao sao tocados.
--
-- Uso:
--   MSYS_NO_PATHCONV=1 docker compose exec -T db bash -c '/opt/mssql-tools18/bin/sqlcmd -C -S localhost -U sa -P "$MSSQL_SA_PASSWORD" -d appdb -v tenant=2 -i /dev/stdin' < database/seed/carga_clientes_limpar.sql

SET NOCOUNT ON;

DELETE FROM dbo.cliente WHERE tenant_id = $(tenant) AND observacoes = N'CARGA_TESTE';
PRINT CONCAT('Removidos: ', @@ROWCOUNT);
