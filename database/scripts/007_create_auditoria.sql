-- 007_create_auditoria.sql
-- Trilha de auditoria de eventos de seguranca. Somente INSERT pela aplicacao (o usuario de runtime recebe
-- DENY de UPDATE/DELETE nesta tabela - ver database/post/grants.sql). Sem FKs: o historico sobrevive a exclusoes.
-- Retencao: definir politica por produto (LGPD). Nao registrar senhas, tokens ou segredos em 'detalhe'.
-- Rollback: DROP TABLE dbo.evento_auditoria;  (destrutivo - perde o historico)

CREATE TABLE dbo.evento_auditoria (
    id              BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT pk_evento_auditoria PRIMARY KEY,
    ocorrido_em     DATETIME2(3)         NOT NULL CONSTRAINT df_auditoria_ocorrido DEFAULT SYSUTCDATETIME(),
    tipo            VARCHAR(50)          NOT NULL,
    resultado       VARCHAR(10)          NOT NULL,
    usuario_id      BIGINT               NULL,
    tenant_id       BIGINT               NULL,
    email_informado NVARCHAR(255)        NULL,
    ip              VARCHAR(45)          NULL,
    correlation_id  VARCHAR(64)          NULL,
    detalhe         NVARCHAR(500)        NULL,
    CONSTRAINT ck_auditoria_resultado CHECK (resultado IN ('SUCESSO', 'FALHA'))
);
CREATE INDEX ix_auditoria_tenant_data ON dbo.evento_auditoria (tenant_id, ocorrido_em DESC);
CREATE INDEX ix_auditoria_usuario ON dbo.evento_auditoria (usuario_id, ocorrido_em DESC);
