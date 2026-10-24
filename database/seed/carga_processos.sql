-- carga_processos.sql (SOMENTE DESENVOLVIMENTO / TESTE DE CARGA - nunca rode em producao)
-- Cria no maximo um processo para cada cliente gerado por carga_clientes.sql no tenant informado.
-- Clientes sao identificados por observacoes = 'CARGA_TESTE' e email do seed (@exemplo.com.br).
-- Pode ser repetido: clientes que ja possuem processo nao recebem outro.
-- Os numeros CNJ sao sinteticos, com digito verificador calculado; nao representam processos reais.
--
-- Uso no PowerShell, com o Docker Compose no ar:
--   docker cp .\database\seed\carga_processos.sql db:/tmp/carga_processos.sql
--   docker compose exec -T db bash -c '/opt/mssql-tools18/bin/sqlcmd -b -C -S localhost -U sa -P "$MSSQL_SA_PASSWORD" -d appdb -v tenant=2 -i /tmp/carga_processos.sql'

SET NOCOUNT ON;

DECLARE @tenant BIGINT = $(tenant);
DECLARE @total BIGINT;
DECLARE @inseridos BIGINT = 0;

IF NOT EXISTS (SELECT 1 FROM dbo.tenant WHERE id = @tenant)
    THROW 50000, 'Tenant inexistente', 1;

CREATE TABLE #carga_processo (
    ordem       BIGINT NOT NULL PRIMARY KEY,
    cliente_id  BIGINT NOT NULL UNIQUE,
    numero      VARCHAR(20) NOT NULL
);

;WITH clientes AS (
    SELECT c.id AS cliente_id,
           ROW_NUMBER() OVER (ORDER BY c.id) AS ordem
    FROM dbo.cliente c
    WHERE c.tenant_id = @tenant
      AND c.observacoes = N'CARGA_TESTE'
      AND c.email LIKE N'%@exemplo.com.br'
),
base AS (
    SELECT cliente_id,
           ordem,
           RIGHT('0000000' + CONVERT(VARCHAR(7), 2000000 + CONVERT(INT, ordem)), 7)
               + '2026' + '8' + '26' + '0100' AS numero_base
    FROM clientes
)
INSERT INTO #carga_processo (ordem, cliente_id, numero)
SELECT ordem,
       cliente_id,
       LEFT(numero_base, 7)
           + RIGHT('00' + CONVERT(VARCHAR(2),
               98 - CONVERT(INT, CONVERT(DECIMAL(38, 0), numero_base + '00') % 97)), 2)
           + SUBSTRING(numero_base, 8, 11)
FROM base;

SELECT @total = COUNT_BIG(*) FROM #carga_processo;

IF @total = 0
    THROW 50001, 'Nenhum cliente de carga encontrado. Rode carga_clientes.sql primeiro.', 1;

IF @total > 7999999
    THROW 50002, 'Limite de clientes excedido para a faixa CNJ sintetica reservada.', 1;

INSERT INTO dbo.processo (
    tenant_id, cliente_id, numero, tipo, area_juridica, tribunal, comarca, vara,
    advogado_nome, advogado_oab, situacao, proximo_prazo, proximo_prazo_descricao,
    observacoes
)
SELECT @tenant,
       c.cliente_id,
       c.numero,
       CASE c.ordem % 7
           WHEN 0 THEN 'CIVIL'
           WHEN 1 THEN 'TRABALHISTA'
           WHEN 2 THEN 'EMPRESARIAL'
           WHEN 3 THEN 'FAMILIA'
           WHEN 4 THEN 'CRIMINAL'
           WHEN 5 THEN 'ADMINISTRATIVO'
           ELSE 'BANCARIO'
       END,
       CASE c.ordem % 7
           WHEN 0 THEN N'Direito Civil'
           WHEN 1 THEN N'Direito do Trabalho'
           WHEN 2 THEN N'Direito Empresarial'
           WHEN 3 THEN N'Direito de Família'
           WHEN 4 THEN N'Direito Penal'
           WHEN 5 THEN N'Direito Administrativo'
           ELSE N'Direito Bancário'
       END,
       N'TJSP',
       cl.cidade,
       CASE c.ordem % 4
           WHEN 0 THEN N'1ª Vara'
           WHEN 1 THEN N'2ª Vara'
           WHEN 2 THEN N'3ª Vara'
           ELSE N'4ª Vara'
       END,
       CASE c.ordem % 5
           WHEN 0 THEN N'Ana Silva'
           WHEN 1 THEN N'Bruno Santos'
           WHEN 2 THEN N'Carla Oliveira'
           WHEN 3 THEN N'Diego Souza'
           ELSE N'Elisa Costa'
       END,
       NULL,
       CASE c.ordem % 10
           WHEN 0 THEN 'URGENTE'
           WHEN 1 THEN 'PENDENTE'
           WHEN 2 THEN 'SUSPENSO'
           WHEN 3 THEN 'ENCERRADO'
           ELSE 'EM_ANDAMENTO'
       END,
       CASE WHEN c.ordem % 10 = 3 THEN NULL
            ELSE DATEADD(DAY, CONVERT(INT, c.ordem % 180) - 30, CONVERT(DATE, SYSUTCDATETIME()))
       END,
       CASE WHEN c.ordem % 10 = 3 THEN NULL
            ELSE N'Prazo processual de teste'
       END,
       N'CARGA_TESTE'
    FROM #carga_processo c
    INNER JOIN dbo.cliente cl
        ON cl.id = c.cliente_id
       AND cl.tenant_id = @tenant
    WHERE NOT EXISTS (
        SELECT 1
        FROM dbo.processo p
        WHERE p.tenant_id = @tenant
          AND p.cliente_id = c.cliente_id
    )
    AND NOT EXISTS (
        SELECT 1
        FROM dbo.processo p
        WHERE p.tenant_id = @tenant
          AND p.numero = c.numero
    );

SET @inseridos = @@ROWCOUNT;

PRINT CONCAT('Clientes de carga encontrados: ', @total,
             ' | processos inseridos agora: ', @inseridos,
             ' | tenant: ', @tenant);
