# CLAUDE.md

Contexto para o Claude Code. Mantenha curto e atualizado. Preencha a secao "Produto" ao iniciar um projeto novo.

## Produto
- **Nome:** _(preencher)_
- **O que e / para quem:** _(1-2 frases; detalhes em `docs/product/PRD.md`)_

## Stack
Java 21 · Spring Boot 3.3 · Maven · SQL Server · Spring Data JPA/Hibernate · Spring Security + JWT (Bearer, stateless) · Docker Compose · GitHub Actions.

## Estrutura
```
backend/                 API Spring Boot (pacote base com.example.app)
  auth/ user/ security/ shared/   modulos; novos modulos de negocio entram como pacotes irmaos
database/scripts/        scripts SQL numerados (NNN_descricao.sql) - fonte da verdade do schema
database/apply.sh        aplica scripts uma vez cada, validando checksum
docs/                    product/ (PRD, checklist), adr/ (decisoes), architecture/, security/
scripts/init-project.ps1 renomeia a casca para um produto novo
```

## Comandos
```bash
cp .env.example .env                    # ajustar valores
docker compose up --build               # banco + scripts + API em :8080
cd backend && mvn verify                # compila e roda todos os testes (integracao exige Docker)
cd backend && mvn test                  # so testes (integracao e ignorada sem Docker)
```
Variaveis de ambiente obrigatorias: `DB_URL`, `DB_USER`, `DB_PASSWORD`, `JWT_SECRET` (>= 32 chars).

## Regras do projeto
1. **Nao use Flyway/Liquibase.** O schema muda por script SQL novo em `database/scripts/`, revisado em PR.
2. **Nunca edite um script ja publicado.** `apply.sh` detecta mudanca de checksum e falha. Crie o proximo numero.
3. Scripts sem `GO`, com `-- Rollback:` documentado no cabecalho. Datas em UTC (`DATETIME2` + `SYSUTCDATETIME()`).
4. Hibernate roda com `ddl-auto=validate`: entidade e script precisam bater (o teste de integracao valida isso).
5. Entidades JPA **nunca** saem pela API; use DTOs (`record`). Controller so recebe/responde; regra fica no Service; `@Transactional` no Service.
6. Autorizacao e feita no servidor (`SecurityConfig` + `@PreAuthorize`). Nunca confie em papel/ID vindo do cliente. A identidade vem de `@AuthenticationPrincipal AuthPrincipal`.
7. Sem segredos no codigo, no `application.yml`, em logs ou commits. Configuracao so por variavel de ambiente.
8. Erros saem como `ApiError` via `GlobalExceptionHandler`; nunca devolva stack trace, SQL ou mensagem de excecao interna.
9. Todo codigo novo de regra de negocio vem com teste. Teste negativo (sem permissao, entrada invalida) vale tanto quanto o caminho feliz.
10. Decisao arquitetural relevante vira ADR em `docs/adr/`.

## Convencoes
- Codigo e pacotes em ingles; tabelas/colunas e mensagens de erro ao usuario em portugues (padrao ja usado).
- API: `/api/v1/...`, JSON, status HTTP corretos, `X-Correlation-Id` em toda resposta.
- Commits pequenos e descritivos.
