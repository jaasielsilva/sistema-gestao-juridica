-- seed-usuarios.sql (SOMENTE TESTE DE CARGA)
-- Cria $(qtd) usuarios de teste carga01@carga.teste, carga02@... no tenant $(tenant), papel USER, e-mail verificado,
-- todos com a mesma senha de teste. O hash BCrypt entra no lugar de __HASH__ (veja README.md; nunca versione a senha).
-- Pode ser repetido: e-mails que ja existem sao ignorados.
-- Remocao: limpar-usuarios.sql

SET NOCOUNT ON;

DECLARE @tenant BIGINT = $(tenant);
DECLARE @qtd INT = $(qtd);

IF NOT EXISTS (SELECT 1 FROM dbo.tenant WHERE id = @tenant)
    THROW 50000, 'Tenant inexistente', 1;

WITH n AS (
    SELECT TOP (@qtd) ROW_NUMBER() OVER (ORDER BY (SELECT NULL)) AS i FROM sys.all_objects
)
INSERT INTO dbo.usuario (nome, email, senha_hash, papel, ativo, tenant_id, email_verificado)
SELECT CONCAT(N'Usuario Carga ', RIGHT('00' + CAST(i AS VARCHAR(3)), 2)),
       CONCAT('carga', RIGHT('00' + CAST(i AS VARCHAR(3)), 2), '@carga.teste'),
       '__HASH__', 'USER', 1, @tenant, 1
FROM n
WHERE NOT EXISTS (SELECT 1 FROM dbo.usuario u
                  WHERE u.email = CONCAT('carga', RIGHT('00' + CAST(n.i AS VARCHAR(3)), 2), '@carga.teste'));

SELECT COUNT(*) AS usuarios_de_carga FROM dbo.usuario WHERE email LIKE 'carga%@carga.teste';
