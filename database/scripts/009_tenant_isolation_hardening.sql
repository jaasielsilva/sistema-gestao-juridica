-- 009_tenant_isolation_hardening.sql
-- Provisions independent workspaces for new registrations and isolates legacy default-tenant accounts.
-- Existing tenant 1 users are split one-per-tenant because the old registration flow collected no organization membership data.
-- Their notes and audit events follow their user; active sessions are revoked so stale tenant claims cannot be reused.
-- Rollback: requires restoring a pre-migration backup; merging legacy accounts back needs verified organization membership.

CREATE SEQUENCE dbo.tenant_id_seq AS BIGINT START WITH 2 INCREMENT BY 1;

ALTER TABLE dbo.tenant DROP CONSTRAINT uq_tenant_nome;

INSERT INTO dbo.tenant (id, nome)
SELECT NEXT VALUE FOR dbo.tenant_id_seq, N'Conta legada ' + CONVERT(NVARCHAR(20), u.id)
FROM dbo.usuario u
WHERE u.tenant_id = 1;

UPDATE u
SET u.tenant_id = t.id
FROM dbo.usuario u
JOIN dbo.tenant t ON t.nome = N'Conta legada ' + CONVERT(NVARCHAR(20), u.id)
WHERE u.tenant_id = 1;

UPDATE n
SET n.tenant_id = u.tenant_id
FROM dbo.nota n
JOIN dbo.usuario u ON u.id = n.criado_por
WHERE n.tenant_id = 1;

UPDATE e
SET e.tenant_id = u.tenant_id
FROM dbo.evento_auditoria e
JOIN dbo.usuario u ON u.id = e.usuario_id
WHERE e.tenant_id = 1;

UPDATE s
SET s.revogada_em = SYSUTCDATETIME()
FROM dbo.sessao s
JOIN dbo.usuario u ON u.id = s.usuario_id
WHERE u.tenant_id <> 1 AND s.revogada_em IS NULL;
