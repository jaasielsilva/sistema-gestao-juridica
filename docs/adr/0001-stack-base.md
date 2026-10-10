# ADR 0001 — Stack base da casca

- **Status:** aceito
- **Data:** 2026-10-10

## Contexto
Queremos iniciar produtos novos rapidamente, com uma base que ja traga autenticacao, banco, Docker e CI.

## Decisoes
| Tema | Decisao | Motivo |
|---|---|---|
| Linguagem/framework | Java 21 + Spring Boot 3.3, Maven | LTS, ecossistema maduro |
| Banco | SQL Server | requisito do dono do projeto |
| Acesso a dados | Spring Data JPA/Hibernate com `ddl-auto=validate` | ORM produtivo sem deixar o Hibernate alterar o schema |
| Schema | Scripts SQL numerados + `schema_version` com checksum; **sem Flyway/Liquibase** | schema revisado em PR e implantacao controlada |
| Autenticacao | e-mail/senha, BCrypt, JWT HS256 (Bearer, stateless) | simples e suficiente para a maioria dos produtos |
| Estrutura | Monolito modular (pacotes por modulo de negocio) | simplicidade; limites claros sem microsservicos |
| Segredos | So variaveis de ambiente; JWT_SECRET >= 32 chars e validado na partida | nenhum segredo em codigo/repositorio |

## Consequencias e pontos a revisitar por produto
- **JWT sem revogacao:** se precisar de logout real/revogacao, adicionar refresh token com armazenamento e rotacao, ou sessao no servidor.
- **HS256 (segredo compartilhado):** se outros servicos precisarem validar tokens, migrar para RS256/ES256 ou um IdP (Entra ID, Keycloak).
- **Multi-tenancy:** nao incluido. Se o produto for SaaS multiempresa, isso precisa ser decidido antes das tabelas de negocio:
  coluna de tenant em toda tabela, chaves unicas compostas, tenant extraido do token no servidor (nunca do cliente) e testes negativos de isolamento.
- **Rate limit** de login nao incluido.
