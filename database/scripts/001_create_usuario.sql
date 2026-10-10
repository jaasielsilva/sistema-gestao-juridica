-- 001_create_usuario.sql
-- Usuarios do sistema (login/cadastro). Senha guardada apenas como hash BCrypt.
-- Rollback: DROP TABLE dbo.usuario;  (destrutivo - nunca em producao sem backup)

CREATE TABLE dbo.usuario (
    id             BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT pk_usuario PRIMARY KEY,
    nome           NVARCHAR(150)        NOT NULL,
    email          NVARCHAR(255)        NOT NULL,
    senha_hash     NVARCHAR(100)        NOT NULL,
    papel          VARCHAR(30)          NOT NULL,
    ativo          BIT                  NOT NULL CONSTRAINT df_usuario_ativo DEFAULT 1,
    criado_em      DATETIME2(3)         NOT NULL CONSTRAINT df_usuario_criado DEFAULT SYSUTCDATETIME(),
    atualizado_em  DATETIME2(3)         NOT NULL CONSTRAINT df_usuario_atualizado DEFAULT SYSUTCDATETIME(),
    CONSTRAINT uq_usuario_email UNIQUE (email),
    CONSTRAINT ck_usuario_papel CHECK (papel IN ('USER', 'ADMIN'))
);
