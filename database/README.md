# Banco de dados (SQL Server)

O schema e definido por scripts em `scripts/`. Nao usamos Flyway nem Liquibase.

## Regras
- Nome: `NNN_descricao_em_minusculas.sql` (3 digitos, ordem de execucao). Validado no CI.
- Um script = uma mudanca coerente. Sem `GO`. Cabecalho com descricao e `-- Rollback:`.
- **Nunca edite um script ja aplicado em qualquer ambiente compartilhado.** Corrija com um script novo.
- Datas em UTC (`DATETIME2(3)`, `SYSUTCDATETIME()`). Defina PK, FK, unicidade e indices junto da tabela.
- Scripts destrutivos (`DROP`, `DELETE`, `TRUNCATE`) so com revisao explicita e backup.
- Nao guarde senha em texto, client secret ou token em coluna sem criptografia.

## Controle de aplicacao
A numeracao sozinha nao evita reaplicacao nem divergencia. Por isso existe `dbo.schema_version` (criada por `000_schema_control.sql`),
com o nome, o checksum SHA-256 e a data de cada script aplicado. O `apply.sh`:
- ignora script ja aplicado com o mesmo checksum;
- **falha** se um script aplicado foi alterado (checksum diferente);
- aplica scripts novos com `-b` (para no primeiro erro) e so registra depois do sucesso.

Para descobrir divergencia entre ambientes, compare a tabela:
```sql
SELECT script, checksum, aplicado_em FROM dbo.schema_version ORDER BY script;
```

## Execucao
- Local: automatico pelo servico `db-scripts` do `docker-compose.yml`.
- Homologacao/producao: rode `apply.sh` (ou os scripts novos, na ordem) a partir de um host com `sqlcmd`, **com um usuario de migracao**,
  separado do usuario que a aplicacao usa em runtime (este so precisa de DML). Faca backup antes.

## Novo script: passo a passo
1. Crie `scripts/00N_descricao.sql` com o proximo numero.
2. Atualize a entidade JPA correspondente (o Hibernate valida o schema).
3. Rode `mvn verify` no `backend/`: o teste de integracao aplica os scripts e valida as entidades.
4. Abra o PR com o script e o codigo juntos.

> O teste de integracao carrega os scripts listados em `backend/src/test/resources/application-test.yml` (`schema-locations`).
> Ao criar um script novo, adicione-o la (lista separada por virgula).
