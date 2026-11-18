-- 016_escavador_colunas_credenciais.sql
-- O token (PAT) do Escavador e longo; cifrado (AES-GCM + base64) passava de 500 caracteres e o INSERT
-- falhava com "String or binary data would be truncated". Amplia as duas colunas de credencial.
-- Rollback: ALTER TABLE dbo.escavador_configuracao ALTER COLUMN token_cifrado NVARCHAR(500) NOT NULL;
--           ALTER TABLE dbo.escavador_configuracao ALTER COLUMN callback_secret_cifrado NVARCHAR(500) NOT NULL;
--           (so se nenhum valor gravado exceder 500 caracteres)

ALTER TABLE dbo.escavador_configuracao ALTER COLUMN token_cifrado NVARCHAR(2000) NOT NULL;
ALTER TABLE dbo.escavador_configuracao ALTER COLUMN callback_secret_cifrado NVARCHAR(2000) NOT NULL;
