-- carga_clientes.sql  (SOMENTE DESENVOLVIMENTO / TESTE DE CARGA - nunca rode em producao)
-- Gera $(qtd) clientes de teste no tenant $(tenant): 75% pessoa fisica, 25% juridica, CPF/CNPJ com digitos
-- verificadores VALIDOS (da para editar pela tela), ~90% ativos, datas de criacao espalhadas em 2 anos.
-- Todo registro gerado leva observacoes = 'CARGA_TESTE'; o script carga_clientes_limpar.sql remove so esses.
-- Pode ser repetido: documentos que ja existem no tenant sao ignorados (so entram os faltantes).
--
-- Uso (da raiz do repositorio, com o compose no ar):
--   MSYS_NO_PATHCONV=1 docker compose exec -T db bash -c '/opt/mssql-tools18/bin/sqlcmd -C -S localhost -U sa -P "$MSSQL_SA_PASSWORD" -d appdb -v tenant=2 qtd=200000 -i /dev/stdin' < database/seed/carga_clientes.sql

SET NOCOUNT ON;

DECLARE @tenant BIGINT = $(tenant);
DECLARE @qtd INT = $(qtd);

IF NOT EXISTS (SELECT 1 FROM dbo.tenant WHERE id = @tenant)
    THROW 50000, 'Tenant inexistente', 1;

DECLARE @inicio DATETIME2(3) = SYSDATETIME();

WITH
n AS (
    SELECT TOP (@qtd) ROW_NUMBER() OVER (ORDER BY (SELECT NULL)) AS i
    FROM sys.all_objects a CROSS JOIN sys.all_objects b CROSS JOIN sys.all_objects c
),
-- Base do documento: injetiva em i (7919 e primo com 10^9), entao nao repete.
base AS (
    SELECT i,
           CASE WHEN i % 4 = 0 THEN 'PJ' ELSE 'PF' END AS tipo,
           RIGHT('000000000' + CAST((CAST(i AS BIGINT) * 7919) % 1000000000 AS VARCHAR(9)), 9) AS cpf9,
           RIGHT('00000000' + CAST((CAST(i AS BIGINT) * 7919) % 100000000 AS VARCHAR(8)), 8) + '0001' AS cnpj12
    FROM n
),
-- CPF: dv1 (pesos 10..2 sobre 9 digitos), dv2 (pesos 11..2 sobre 10 digitos).
cpf1 AS (
    SELECT b.*, (CAST(SUBSTRING(b.cpf9, 1, 1) AS INT) * 10 + CAST(SUBSTRING(b.cpf9, 2, 1) AS INT) * 9 + CAST(SUBSTRING(b.cpf9, 3, 1) AS INT) * 8 + CAST(SUBSTRING(b.cpf9, 4, 1) AS INT) * 7 + CAST(SUBSTRING(b.cpf9, 5, 1) AS INT) * 6 + CAST(SUBSTRING(b.cpf9, 6, 1) AS INT) * 5 + CAST(SUBSTRING(b.cpf9, 7, 1) AS INT) * 4 + CAST(SUBSTRING(b.cpf9, 8, 1) AS INT) * 3 + CAST(SUBSTRING(b.cpf9, 9, 1) AS INT) * 2) AS s1
    FROM base b
),
cpf2 AS (
    SELECT c.*, cpf9 + CAST(CASE WHEN (s1 * 10) % 11 = 10 THEN 0 ELSE (s1 * 10) % 11 END AS CHAR(1)) AS cpf10
    FROM cpf1 c
),
cpf3 AS (
    SELECT c.*, (CAST(SUBSTRING(c.cpf10, 1, 1) AS INT) * 11 + CAST(SUBSTRING(c.cpf10, 2, 1) AS INT) * 10 + CAST(SUBSTRING(c.cpf10, 3, 1) AS INT) * 9 + CAST(SUBSTRING(c.cpf10, 4, 1) AS INT) * 8 + CAST(SUBSTRING(c.cpf10, 5, 1) AS INT) * 7 + CAST(SUBSTRING(c.cpf10, 6, 1) AS INT) * 6 + CAST(SUBSTRING(c.cpf10, 7, 1) AS INT) * 5 + CAST(SUBSTRING(c.cpf10, 8, 1) AS INT) * 4 + CAST(SUBSTRING(c.cpf10, 9, 1) AS INT) * 3 + CAST(SUBSTRING(c.cpf10, 10, 1) AS INT) * 2) AS s2
    FROM cpf2 c
),
-- CNPJ: dv1 (pesos 5,4,3,2,9,8,7,6,5,4,3,2), dv2 (pesos 6,5,4,3,2,9,8,7,6,5,4,3,2).
cnpj1 AS (
    SELECT c.*, (CAST(SUBSTRING(c.cnpj12, 1, 1) AS INT) * 5 + CAST(SUBSTRING(c.cnpj12, 2, 1) AS INT) * 4 + CAST(SUBSTRING(c.cnpj12, 3, 1) AS INT) * 3 + CAST(SUBSTRING(c.cnpj12, 4, 1) AS INT) * 2 + CAST(SUBSTRING(c.cnpj12, 5, 1) AS INT) * 9 + CAST(SUBSTRING(c.cnpj12, 6, 1) AS INT) * 8 + CAST(SUBSTRING(c.cnpj12, 7, 1) AS INT) * 7 + CAST(SUBSTRING(c.cnpj12, 8, 1) AS INT) * 6 + CAST(SUBSTRING(c.cnpj12, 9, 1) AS INT) * 5 + CAST(SUBSTRING(c.cnpj12, 10, 1) AS INT) * 4 + CAST(SUBSTRING(c.cnpj12, 11, 1) AS INT) * 3 + CAST(SUBSTRING(c.cnpj12, 12, 1) AS INT) * 2) AS t1
    FROM cpf3 c
),
cnpj2 AS (
    SELECT c.*, cnpj12 + CAST(CASE WHEN (t1 * 10) % 11 = 10 THEN 0 ELSE (t1 * 10) % 11 END AS CHAR(1)) AS cnpj13
    FROM cnpj1 c
),
cnpj3 AS (
    SELECT c.*, (CAST(SUBSTRING(c.cnpj13, 1, 1) AS INT) * 6 + CAST(SUBSTRING(c.cnpj13, 2, 1) AS INT) * 5 + CAST(SUBSTRING(c.cnpj13, 3, 1) AS INT) * 4 + CAST(SUBSTRING(c.cnpj13, 4, 1) AS INT) * 3 + CAST(SUBSTRING(c.cnpj13, 5, 1) AS INT) * 2 + CAST(SUBSTRING(c.cnpj13, 6, 1) AS INT) * 9 + CAST(SUBSTRING(c.cnpj13, 7, 1) AS INT) * 8 + CAST(SUBSTRING(c.cnpj13, 8, 1) AS INT) * 7 + CAST(SUBSTRING(c.cnpj13, 9, 1) AS INT) * 6 + CAST(SUBSTRING(c.cnpj13, 10, 1) AS INT) * 5 + CAST(SUBSTRING(c.cnpj13, 11, 1) AS INT) * 4 + CAST(SUBSTRING(c.cnpj13, 12, 1) AS INT) * 3 + CAST(SUBSTRING(c.cnpj13, 13, 1) AS INT) * 2) AS t2
    FROM cnpj2 c
),
doc AS (
    SELECT i, tipo,
           CASE WHEN tipo = 'PF'
                THEN cpf10 + CAST(CASE WHEN (s2 * 10) % 11 = 10 THEN 0 ELSE (s2 * 10) % 11 END AS CHAR(1))
                ELSE cnpj13 + CAST(CASE WHEN (t2 * 10) % 11 = 10 THEN 0 ELSE (t2 * 10) % 11 END AS CHAR(1))
           END AS documento
    FROM cnpj3
),
nomes AS (
    SELECT d.i, d.tipo, d.documento,
           (SELECT v.n FROM (VALUES (0,N'Ana'),(1,N'Bruno'),(2,N'Carla'),(3,N'Diego'),(4,N'Elisa'),(5,N'Fabio'),(6,N'Gabriela'),
                                    (7,N'Hugo'),(8,N'Isabela'),(9,N'Joao'),(10,N'Karen'),(11,N'Lucas'),(12,N'Marina'),(13,N'Nelson'),
                                    (14,N'Olivia'),(15,N'Paulo'),(16,N'Rafaela'),(17,N'Sergio'),(18,N'Tatiana'),(19,N'Vitor')) v(k, n)
            WHERE v.k = d.i % 20) AS prenome,
           (SELECT v.n FROM (VALUES (0,N'Silva'),(1,N'Souza'),(2,N'Oliveira'),(3,N'Santos'),(4,N'Pereira'),(5,N'Lima'),(6,N'Costa'),
                                    (7,N'Ribeiro'),(8,N'Almeida'),(9,N'Carvalho'),(10,N'Gomes'),(11,N'Martins'),(12,N'Rocha'),
                                    (13,N'Barbosa'),(14,N'Moreira'),(15,N'Nunes'),(16,N'Araujo'),(17,N'Cardoso'),(18,N'Teixeira'),(19,N'Fernandes')) v(k, n)
            WHERE v.k = (d.i / 20) % 20) AS sobrenome,
           (SELECT v.n FROM (VALUES (0,N'Comercio'),(1,N'Tecnologia'),(2,N'Servicos'),(3,N'Industria'),(4,N'Transportes'),
                                    (5,N'Consultoria'),(6,N'Construtora'),(7,N'Alimentos')) v(k, n)
            WHERE v.k = (d.i / 7) % 8) AS ramo,
           (SELECT v.c FROM (VALUES (0,N'Sao Paulo',N'SP'),(1,N'Rio de Janeiro',N'RJ'),(2,N'Belo Horizonte',N'MG'),(3,N'Curitiba',N'PR'),
                                    (4,N'Porto Alegre',N'RS'),(5,N'Salvador',N'BA'),(6,N'Recife',N'PE'),(7,N'Fortaleza',N'CE'),
                                    (8,N'Brasilia',N'DF'),(9,N'Goiania',N'GO')) v(k, c, u)
            WHERE v.k = d.i % 10) AS cidade,
           (SELECT v.u FROM (VALUES (0,N'Sao Paulo',N'SP'),(1,N'Rio de Janeiro',N'RJ'),(2,N'Belo Horizonte',N'MG'),(3,N'Curitiba',N'PR'),
                                    (4,N'Porto Alegre',N'RS'),(5,N'Salvador',N'BA'),(6,N'Recife',N'PE'),(7,N'Fortaleza',N'CE'),
                                    (8,N'Brasilia',N'DF'),(9,N'Goiania',N'GO')) v(k, c, u)
            WHERE v.k = d.i % 10) AS uf
    FROM doc d
    WHERE d.documento <> REPLICATE(LEFT(d.documento, 1), LEN(d.documento))
)
INSERT INTO dbo.cliente (tenant_id, tipo, nome, documento, email, telefone, cep, logradouro, numero, bairro, cidade, uf,
                         observacoes, ativo, criado_em, atualizado_em)
SELECT @tenant,
       x.tipo,
       CASE WHEN x.tipo = 'PF' THEN x.prenome + N' ' + x.sobrenome + N' ' + CAST(x.i AS NVARCHAR(10))
            ELSE x.sobrenome + N' ' + x.ramo + N' Ltda ' + CAST(x.i AS NVARCHAR(10)) END,
       x.documento,
       LOWER(x.prenome) + N'.' + LOWER(x.sobrenome) + CAST(x.i AS NVARCHAR(10)) + N'@exemplo.com.br',
       '11' + RIGHT('000000000' + CAST(900000000 + (CAST(x.i AS BIGINT) * 104729) % 99999999 AS VARCHAR(9)), 9),
       RIGHT('00000000' + CAST(1000000 + (CAST(x.i AS BIGINT) * 31) % 98999999 AS VARCHAR(8)), 8),
       N'Rua ' + x.sobrenome,
       CAST(1 + x.i % 3000 AS NVARCHAR(20)),
       N'Centro',
       x.cidade,
       x.uf,
       N'CARGA_TESTE',
       CASE WHEN x.i % 10 = 0 THEN 0 ELSE 1 END,
       DATEADD(MINUTE, -((CAST(x.i AS BIGINT) * 37) % 1051200), SYSUTCDATETIME()),
       SYSUTCDATETIME()
FROM nomes x
WHERE NOT EXISTS (SELECT 1 FROM dbo.cliente c WHERE c.tenant_id = @tenant AND c.documento = x.documento);

DECLARE @inseridos INT = @@ROWCOUNT;
DECLARE @total INT = (SELECT COUNT(*) FROM dbo.cliente WHERE tenant_id = @tenant);
PRINT CONCAT('Inseridos: ', @inseridos, ' | total do tenant ', @tenant, ': ', @total,
             ' | tempo (ms): ', DATEDIFF(MILLISECOND, @inicio, SYSDATETIME()));
