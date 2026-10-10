-- 006_create_login_tentativa.sql
-- Controle de tentativas (limite de taxa / bloqueio temporario) por chave: e-mail (hash), IP, usuario...
-- Fica no banco para valer com varias instancias da API e para funcionar tambem com e-mails inexistentes
-- (nao revela quais contas existem). A chave de e-mail guarda o hash, nao o endereco.
-- Rollback: DROP TABLE dbo.login_tentativa;

CREATE TABLE dbo.login_tentativa (
    id            BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT pk_login_tentativa PRIMARY KEY,
    chave         VARCHAR(200)         NOT NULL,
    tentativas    INT                  NOT NULL CONSTRAINT df_login_tentativa_n DEFAULT 0,
    janela_inicio DATETIME2(3)         NOT NULL,
    bloqueado_ate DATETIME2(3)         NULL,
    CONSTRAINT uq_login_tentativa_chave UNIQUE (chave)
);
