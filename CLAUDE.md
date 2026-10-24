# CLAUDE.md

Contexto para o Claude Code. Mantenha curto e atualizado. Preencha a seção "Produto" ao iniciar um projeto novo.
Estado completo da aplicação: `docs/ESTADO-ATUAL.md`.

## Produto
- **Nome:** Sistema de Gestão Jurídica
- **O que é / para quem:** gestão de escritórios de advocacia (clientes, processos, prazos, documentos, financeiro), multi-tenant; perfis e permissões propostos em `docs/product/PRD.md`. Módulos implementados até agora: **clientes** e **processos**.

## Stack
- Backend: Java 21 · Spring Boot 3.5 · Maven · SQL Server · Spring Data JPA/Hibernate · Spring Security + JWT
- Frontend: Angular 22 (standalone, signals, TS estrito) · Angular Material 3 · Vitest
- Infra: Docker Compose · nginx · GitHub Actions · Trivy

## Estrutura
```
backend/                 API (pacote base br.com.jaasielsilva.gestaojuridica)
  auth/ (session, mfa, throttle, token)  user/  security/  tenant/  audit/  mail/  shared/  cliente/  processo/  nota/(EXEMPLO)
frontend/                SPA: core/ (auth, config, navegação, tema), layout/ (shell, header, sidebar, footer),
                         features/ (telas: auth, account, clientes, processos, home), shared/ (br-format)
database/scripts/        scripts SQL numerados (NNN_descricao.sql) - fonte da verdade do schema
database/seed/           cargas opcionais de dados de teste (não rodam no apply.sh)
database/post/grants.sql permissões do usuário de runtime (roda a cada apply.sh)
database/apply.sh        aplica scripts uma vez cada, valida checksum, cria o usuário de runtime
docs/                    ESTADO-ATUAL.md, product/ (PRD, checklist), adr/ (decisões)
scripts/init-project.ps1 renomeia a casca para um produto novo
```

## Comandos
```bash
cp .env.example .env && docker compose up --build   # tudo: banco, API :8080, frontend :3000, Mailpit :8025
cd backend  && mvn verify                           # build + testes (integração exige Docker)
cd frontend && npm start                            # dev em :4200 com proxy /api -> :8080
cd frontend && npx ng test --watch=false && npx ng build
```

## Regras do projeto
1. **Não use Flyway/Liquibase.** O schema muda por script SQL novo em `database/scripts/`, revisado em PR.
2. **Nunca edite um script já publicado.** `apply.sh` detecta mudança de checksum e falha. Crie o próximo número.
3. Scripts sem `GO` e sem blocos `BEGIN...END` (o teste de integração os aplica via JDBC), com `-- Rollback:` no cabeçalho. Datas em UTC (`DATETIME2` + `SYSUTCDATETIME()`). Os testes aplicam automaticamente todos os `NNN_*.sql`.
4. Hibernate roda com `ddl-auto=validate`: entidade e script precisam bater (o teste de integração valida isso).
5. Entidades JPA **nunca** saem pela API; use DTOs (`record`). Controller só recebe/responde; regra fica no Service; `@Transactional` no Service.
6. Autorização no servidor (`SecurityConfig` + `@PreAuthorize`). Nunca confie em papel/ID/tenant vindo do cliente: a identidade vem de `@AuthenticationPrincipal AuthPrincipal`.
7. **Multi-tenancy:** toda entidade de negócio estende `TenantScopedEntity` e todo repositório estende `TenantScopedRepository` (modelo: `cliente/`, `processo/`; `nota/` é só exemplo). Nunca use `EntityManager.find`/`getReferenceById` direto nelas: o Hibernate **não** filtra tenant nesses caminhos. Toda tabela de negócio tem `tenant_id NOT NULL` + FK + índice com `tenant_id` na frente. Todo módulo novo precisa de teste de isolamento entre dois tenants.
8. Sem segredos no código, no `application.yml`, em logs ou commits. Configuração só por variável de ambiente.
9. Erros saem como `ApiError` via `GlobalExceptionHandler`; nunca devolva stack trace, SQL ou mensagem de exceção interna.
10. Eventos de segurança novos vão para `AuditService` (nunca registrar senha/token/segredo no detalhe).
11. Todo código novo de regra de negócio vem com teste. Teste negativo (sem permissão, entrada inválida, outro tenant) vale tanto quanto o caminho feliz.
12. Decisão arquitetural relevante vira ADR em `docs/adr/`. Atualize `docs/ESTADO-ATUAL.md` quando o estado mudar.
13. O app roda no banco com o usuário `app_runtime` (sem DDL; sem UPDATE/DELETE na auditoria). Mudou permissão? Edite `database/post/grants.sql`.

## Frontend
- Tela nova: pasta em `features/<nome>`, rota em `app.routes.ts` (filhos do `Shell`), item em `core/navigation/nav-items.ts`.
- Chamadas HTTP sempre por `APP_CONFIG.apiBase` (`/api/v1`); o interceptor anexa o token, renova em 401 e marca `X-Requested-With`. **O access token fica só em memória**: não guarde token em `localStorage`/`sessionStorage`. O refresh token é cookie `HttpOnly` (o JS não o vê).
- Guard e `roles` do menu são só UX; a autorização real é do backend.
- Tema/cores em `src/styles.scss`. Padrão visual: componentes Angular Material, tokens `--mat-sys-*`, sem cor hardcoded. Classes globais de formulário: `.form-stack`, `.form-error`, `.form-success`, `.form-submit`, `.form-links`.
- Não reative a otimização `inlineCritical` do build: ela injeta script inline e quebra a CSP do nginx.

## Ambiente e armadilhas conhecidas
- Docker 29+: o Testcontainers precisa de `docker.api.version` (já fixado no `pom.xml`).
- Git Bash no Windows converte caminhos `/...` em argumentos: use `MSYS_NO_PATHCONV=1` ao chamar `docker run`/`docker exec` com caminhos Linux.
- Dependências: após upgrade do Spring Boot, rode a varredura Trivy no JAR (`docs/ESTADO-ATUAL.md` §Vulnerabilidades) e revise `.trivyignore.yaml`.

## Convenções
- Código e pacotes em inglês; tabelas/colunas e mensagens de erro ao usuário em português (padrão já usado).
- API: `/api/v1/...`, JSON, status HTTP corretos, `X-Correlation-Id` em toda resposta.
- Commits pequenos e descritivos.
