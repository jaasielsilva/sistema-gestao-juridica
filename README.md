# Casca generica de produto

Template pronto para iniciar um produto novo: **Spring Boot 3 + Java 21 + SQL Server**, com login/cadastro (JWT), scripts SQL versionados, Docker e CI.

## O que ja vem pronto
- Cadastro, login e `GET /api/v1/auth/me` (BCrypt, JWT assinado, erro de login que nao revela se o e-mail existe).
- Seguranca: API stateless, CORS por variavel, rota `/api/v1/admin/**` so para `ADMIN`, respostas 401/403 em JSON.
- Tratamento uniforme de erros (`ApiError`) e `X-Correlation-Id` por requisicao.
- Banco: scripts SQL numerados + controle de scripts aplicados (checksum), sem Flyway/Liquibase.
- Docker Compose (SQL Server + aplicacao de scripts + API), Dockerfile multi-stage com usuario nao-root.
- CI no GitHub Actions: build, testes (incluindo SQL Server real via Testcontainers), validacao dos scripts e build da imagem.
- `CLAUDE.md`, template de PRD, checklist de lancamento e ADR inicial.

## Comecando um produto novo
1. Copie esta pasta (ou use como template do GitHub) para o novo repositorio.
2. Renomeie para o produto:
   ```powershell
   .\scripts\init-project.ps1 -Package br.com.minhaempresa.produto -ArtifactId produto
   ```
3. Preencha `CLAUDE.md` (secao Produto) e `docs/product/PRD.md`.
4. Suba o ambiente:
   ```bash
   cp .env.example .env     # troque DB_PASSWORD e JWT_SECRET
   docker compose up --build
   ```
5. API em http://localhost:8080 · Swagger em http://localhost:8080/swagger-ui.html (so com `SWAGGER_ENABLED=true`).

## Testando rapido
```bash
curl -X POST localhost:8080/api/v1/auth/register -H "Content-Type: application/json" \
  -d '{"nome":"Maria","email":"maria@x.com","senha":"senha-forte-123"}'
curl -X POST localhost:8080/api/v1/auth/login -H "Content-Type: application/json" \
  -d '{"email":"maria@x.com","senha":"senha-forte-123"}'
```

## Rodando testes
```bash
cd backend
mvn test      # unitarios (integracao e ignorada se nao houver Docker)
mvn verify    # tudo; exige Docker para o SQL Server de teste
```

## Criando um admin
Auto-cadastro sempre cria `USER`. Para promover alguem, com consciencia do que esta fazendo, via script SQL novo e revisado
(ex.: `002_promover_admin_inicial.sql`) ou direto no banco do ambiente: `UPDATE dbo.usuario SET papel='ADMIN' WHERE email='...'`.

## Limites conhecidos (a decidir por produto)
- Sem refresh token, sem logout/revogacao de token (o JWT vale ate expirar), sem recuperacao de senha, sem verificacao de e-mail.
- Sem rate limit/bloqueio por tentativas de login (coloque no gateway/WAF ou adicione ao produto).
- Sem multi-tenancy. Se o produto for SaaS multiempresa, decida isso **antes** de criar as primeiras tabelas de negocio (ver `docs/adr/0001-stack-base.md`).
- `trustServerCertificate=true` nos exemplos locais; em producao use certificado valido.

Veja `database/README.md` para o processo de scripts SQL.
