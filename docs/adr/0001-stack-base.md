# ADR 0001 — Stack base da casca

- **Status:** aceito (as decisões de autenticação foram substituídas pelo [ADR 0002](0002-sessoes-e-autenticacao.md); multi-tenancy no [ADR 0003](0003-multi-tenancy.md))
- **Data:** 2026-10-10

## Contexto
Queremos iniciar produtos novos rapidamente, com uma base que já traga autenticação, banco, Docker e CI.

## Decisões
| Tema | Decisão | Motivo |
|---|---|---|
| Linguagem/framework | Java 21 + Spring Boot 3.5, Maven | LTS, ecossistema maduro |
| Frontend | Angular 22 + Angular Material 3 (standalone, signals, TS estrito) | Biblioteca oficial, acessível, tema claro/escuro |
| Banco | SQL Server | requisito do dono do projeto |
| Acesso a dados | Spring Data JPA/Hibernate com `ddl-auto=validate` | ORM produtivo sem deixar o Hibernate alterar o schema |
| Schema | Scripts SQL numerados + `schema_version` com checksum; **sem Flyway/Liquibase** | schema revisado em PR e implantação controlada |
| Usuário do banco | A aplicação usa um usuário de **runtime** sem DDL (`app_runtime`), criado por `post/grants.sql` | Menor privilégio |
| Estrutura | Monólito modular (pacotes por módulo de negócio) | simplicidade; limites claros sem microsserviços |
| Segredos | Só variáveis de ambiente; validados na partida (`AppProperties`) | nenhum segredo em código/repositório |
| Entrega | Docker Compose local; nginx serve o SPA e faz proxy de `/api`; HTTPS por override | Mesmo domínio: sem CORS |
| Qualidade | CI com testes de integração em SQL Server real (Testcontainers), varredura Trivy (JAR e imagens), `npm audit`, Dependabot | Falhas e CVEs aparecem antes de produção |

## Pontos a revisitar por produto
- **HS256** (segredo compartilhado) → RS256/ES256 ou IdP se houver outros serviços.
- **Spring Boot 3.5**: o suporte de código aberto da linha 3.x encerrou em 2026; migrar para o Boot 4 (Spring Framework 7) está planejado e destrava as CVEs aceitas em `.trivyignore.yaml`.
- Rate limit global da API (além do login), WAF/CAPTCHA, notificações de login suspeito.
