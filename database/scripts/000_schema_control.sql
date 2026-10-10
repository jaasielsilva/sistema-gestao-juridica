-- 000_schema_control.sql
-- Tabela de controle: registra quais scripts ja foram aplicados neste ambiente e o checksum de cada um.
-- Idempotente: pode ser executado mais de uma vez sem efeito.
-- Rollback: DROP TABLE dbo.schema_version;  (so em ambiente descartavel)

IF OBJECT_ID(N'dbo.schema_version', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.schema_version (
        id          INT IDENTITY(1,1) NOT NULL CONSTRAINT pk_schema_version PRIMARY KEY,
        script      VARCHAR(200)      NOT NULL CONSTRAINT uq_schema_version_script UNIQUE,
        checksum    CHAR(64)          NOT NULL,
        aplicado_em DATETIME2(3)      NOT NULL CONSTRAINT df_schema_version_aplicado DEFAULT SYSUTCDATETIME(),
        aplicado_por NVARCHAR(128)    NOT NULL CONSTRAINT df_schema_version_por DEFAULT SUSER_SNAME()
    );
END
