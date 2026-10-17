# Banco de dados (SQL Server)

O schema é definido por scripts em `scripts/`. Não usamos Flyway nem Liquibase.

## Regras
- Nome: `NNN_descricao_em_minusculas.sql` (3 dígitos, sem buracos nem repetição). Validado no CI.
- Um script = uma mudança coerente. **Sem `GO`** e **sem blocos `BEGIN...END`** (o teste de integração aplica os scripts via JDBC, que separa por `;`). Cabeçalho com descrição e `-- Rollback:`.
- **Nunca edite um script já aplicado em qualquer ambiente compartilhado.** Corrija com um script novo.
- Datas em UTC (`DATETIME2(3)`, `SYSUTCDATETIME()`). Defina PK, FK, unicidade e índices junto da tabela.
- Tabela de negócio: `tenant_id BIGINT NOT NULL` + FK para `tenant` + índice com `tenant_id` na frente (veja `008_create_nota.sql`).
- Scripts destrutivos (`DROP`, `DELETE`, `TRUNCATE`) só com revisão explícita e backup.
- Não guarde senha, client secret ou token em coluna sem proteção (hash para tokens/senhas; criptografia para segredos recuperáveis).

## Scripts atuais
| Script | Conteúdo |
|---|---|
| `000_schema_control.sql` | Tabela `schema_version` (controle dos scripts aplicados). Só o `apply.sh` usa. |
| `001_create_usuario.sql` | Usuários |
| `002_create_tenant.sql` | Tenants (empresas); usuários existentes vão para o tenant padrão (id 1) |
| `003_usuario_seguranca.sql` | Verificação de e-mail e MFA no usuário (usuários antigos ficam verificados) |
| `004_create_sessao.sql` | Sessões e refresh tokens (só o hash do token) |
| `005_create_tokens_e_mfa.sql` | Tokens de e-mail/redefinição (hash) e códigos de recuperação do MFA (hash) |
| `006_create_login_tentativa.sql` | Controle de tentativas / bloqueio |
| `007_create_auditoria.sql` | Trilha de auditoria (somente INSERT pela aplicação) |
| `008_create_nota.sql` | **Exemplo** de tabela com tenant (apague no produto real) |
| `009_tenant_isolation_hardening.sql` | Provisiona IDs de tenants e separa os usuarios legados do tenant padrao |

## Controle de aplicação
A numeração sozinha não evita reaplicação nem divergência. Por isso existe `dbo.schema_version`, com nome, checksum SHA-256 e data de cada script aplicado. O `apply.sh`:
- ignora script já aplicado com o mesmo checksum;
- **falha** se um script aplicado foi alterado (checksum diferente);
- aplica scripts novos com `-b` (para no primeiro erro) e só registra depois do sucesso.

Para descobrir divergência entre ambientes, compare a tabela:
```sql
SELECT script, checksum, aplicado_em FROM dbo.schema_version ORDER BY script;
```

## Usuário de runtime (menor privilégio)
A aplicação **não** usa o `sa`. Depois dos scripts, o `apply.sh` executa `post/grants.sql`, que cria/atualiza o usuário indicado em `APP_DB_USER`/`APP_DB_PASSWORD` com:
- `db_datareader` + `db_datawriter` (ler e escrever dados), **sem DDL** (não cria nem apaga tabelas);
- `DENY INSERT/UPDATE/DELETE` em `schema_version`;
- `DENY UPDATE/DELETE` em `evento_auditoria` (a aplicação só acrescenta eventos, não reescreve o histórico).

A senha (`APP_DB_PASSWORD`) precisa ter 12+ caracteres entre letras, números e `@#%^*_+=.!-` e atender à política do SQL Server (3 de 4 classes). O `apply.sh` valida e explica o erro.

> Importante: o DBA/usuário com direito de DDL (o que roda o `apply.sh`) é **outro** usuário e não deve ser o que a aplicação usa.
> Um administrador do banco ainda pode alterar a auditoria; imutabilidade real exige controles de infraestrutura (veja `docs/ESTADO-ATUAL.md`).

## Execução
- Local: automático pelo serviço `db-scripts` do `docker-compose.yml`.
- Homologação/produção: rode `apply.sh` (ou os scripts novos, na ordem) a partir de um host com `sqlcmd`, **com um usuário de migração**, separado do usuário de runtime. Faça backup antes.

## Novo script: passo a passo
1. Crie `scripts/00N_descricao.sql` com o próximo número.
2. Atualize a entidade JPA correspondente (o Hibernate valida o schema).
3. Rode `mvn verify` no `backend/`: o teste de integração aplica **todos** os scripts e valida as entidades.
4. Se a tabela for nova e de negócio, inclua `tenant_id` e um teste de isolamento entre tenants.
5. Abra o PR com o script e o código juntos.
