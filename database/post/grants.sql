-- post/grants.sql  (executado pelo apply.sh a cada execucao; idempotente; NAO e registrado em schema_version)
-- Cria/atualiza o usuario de RUNTIME da aplicacao com o minimo de permissoes: le e escreve dados, sem DDL.
-- Variaveis sqlcmd: APP_DB_USER, APP_DB_PASSWORD (vem do ambiente; nunca ficam neste arquivo).

IF NOT EXISTS (SELECT 1 FROM sys.server_principals WHERE name = N'$(APP_DB_USER)')
    CREATE LOGIN [$(APP_DB_USER)] WITH PASSWORD = N'$(APP_DB_PASSWORD)', CHECK_POLICY = ON;
ELSE
    ALTER LOGIN [$(APP_DB_USER)] WITH PASSWORD = N'$(APP_DB_PASSWORD)';

IF NOT EXISTS (SELECT 1 FROM sys.database_principals WHERE name = N'$(APP_DB_USER)')
    CREATE USER [$(APP_DB_USER)] FOR LOGIN [$(APP_DB_USER)];

ALTER ROLE db_datareader ADD MEMBER [$(APP_DB_USER)];
ALTER ROLE db_datawriter ADD MEMBER [$(APP_DB_USER)];

-- NEXT VALUE FOR needs UPDATE permission on the sequence; the datawriter role does not grant it.
GRANT UPDATE ON OBJECT::dbo.tenant_id_seq TO [$(APP_DB_USER)];

-- A aplicacao nao pode mexer no controle de scripts nem reescrever/apagar a auditoria.
DENY INSERT, UPDATE, DELETE ON OBJECT::dbo.schema_version TO [$(APP_DB_USER)];
DENY UPDATE, DELETE ON OBJECT::dbo.evento_auditoria TO [$(APP_DB_USER)];
