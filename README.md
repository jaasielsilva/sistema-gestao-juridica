# Casca genérica de produto

Template pronto para iniciar um produto novo: **Spring Boot 3.5 + Java 21 + SQL Server** no backend e **Angular 22 + Angular Material** no frontend, com autenticação completa, segurança reforçada, multi-tenancy, Docker e CI.

> Estado detalhado, controles de segurança, evidências de teste e limitações: **[docs/ESTADO-ATUAL.md](docs/ESTADO-ATUAL.md)**.

## O que já vem pronto

**Autenticação e conta**
- Cadastro (pode ser desligado), login, logout, "sair de todos os dispositivos" e troca de senha.
- Confirmação de e-mail e recuperação de senha por link de uso único.
- MFA (TOTP: Google/Microsoft Authenticator, Authy…) com códigos de recuperação.
- Sessões com *access token* curto (15 min, só em memória no navegador) + *refresh token* rotativo em cookie `HttpOnly`, com detecção de roubo.

**Segurança**
- Bloqueio por tentativas (por e-mail e por IP), no backend e no nginx.
- Auditoria imutável de eventos de segurança (consultável por ADMIN).
- Multi-tenancy com isolamento automático (e testado) entre empresas.
- Usuário de banco de runtime com permissões mínimas, HTTPS/HSTS prontos, cabeçalhos de segurança e CSP.
- Varredura de vulnerabilidades (Trivy + npm audit) no CI e Dependabot.

**Interface**: tela de login/cadastro/recuperação, layout com cabeçalho, menu lateral e rodapé fixo, tema claro/escuro, página de segurança da conta.

**Infra**: Docker Compose (SQL Server, scripts SQL com checksum, API, frontend, Mailpit), CI no GitHub Actions, `CLAUDE.md`, PRD, checklist de lançamento e ADRs.

## Começando um produto novo
1. Copie esta pasta (ou use como template do GitHub) para o novo repositório.
2. Renomeie para o produto:
   ```powershell
   .\scripts\init-project.ps1 -Package br.com.minhaempresa.produto -ArtifactId produto
   ```
3. Preencha `CLAUDE.md` (seção Produto) e `docs/product/PRD.md`.
4. Gere os segredos e suba o ambiente:
   ```bash
   cp .env.example .env     # edite: DB_PASSWORD, APP_DB_PASSWORD, JWT_SECRET, MFA_ENCRYPTION_KEY
   docker compose up --build
   ```
5. Acesse:
   | O quê | Onde |
   |---|---|
   | Aplicação | http://localhost:3000 |
   | API / Swagger | http://localhost:8080 · http://localhost:8080/swagger-ui.html (só com `SWAGGER_ENABLED=true`) |
   | Caixa de e-mail de testes (Mailpit) | http://localhost:8025 |
6. Crie sua conta em **Criar conta**; o link de confirmação chega no Mailpit.

Todas as portas ficam presas a `127.0.0.1` (só o seu computador acessa).

### Virar administrador
O cadastro sempre cria `USER`. Para promover alguém, faça conscientemente, com script SQL revisado ou direto no banco do ambiente:
```sql
UPDATE dbo.usuario SET papel = 'ADMIN' WHERE email = 'voce@exemplo.com';
```
(O papel vai no token: saia e entre de novo.)

## Testando
```bash
cd backend  && mvn verify                  # unitários + integração (exige Docker; usa SQL Server real)
cd frontend && npx ng test --watch=false   # testes do frontend
cd frontend && npx ng build                # build de produção
```

## HTTPS
```bash
bash infrastructure/https/gen-dev-cert.sh   # certificado AUTOASSINADO, só para teste local
docker compose -f docker-compose.yml -f docker-compose.https.yml up --build
# https://localhost:8443
```
Em produção use um certificado real (Let's Encrypt, load balancer da nuvem) e `COOKIE_SECURE=true`.

## Desenvolvimento sem Docker (usando seu SQL Server local)
1. Aplique `database/scripts/*.sql` no seu banco (ex.: `GENERICOPRIVILEGIADO`), em ordem, com `sqlcmd` ou SSMS (ignore o `000`, que só serve ao `apply.sh`; crie a tabela `schema_version` se quiser o controle).
2. Crie um login SQL com permissão de leitura/escrita no banco e defina as variáveis **no terminal** (não grave senha em arquivo versionado):
   ```powershell
   $env:DB_URL="jdbc:sqlserver://localhost:1433;databaseName=GENERICOPRIVILEGIADO;encrypt=true;trustServerCertificate=true"
   $env:DB_USER="seu_usuario_sql"; $env:DB_PASSWORD="sua_senha"
   $env:JWT_SECRET="um-segredo-aleatorio-com-mais-de-32-caracteres"
   $env:MFA_ENCRYPTION_KEY="<saída de: openssl rand -base64 32>"
   $env:REGISTRATION_ENABLED="true"; $env:COOKIE_SECURE="false"
   cd backend; mvn spring-boot:run
   ```
   Requer SQL Authentication e TCP/IP ativos na porta 1433. Sem `SPRING_MAIL_HOST` os e-mails não saem (aparece só um aviso no log); para testar os links, rode o Mailpit: `docker run -p 8025:8025 -p 1025:1025 axllent/mailpit` e defina `SPRING_MAIL_HOST=localhost`, `SPRING_MAIL_PORT=1025`.
3. Frontend: `cd frontend; npm install; npm start` → http://localhost:4200 (o `proxy.conf.json` encaminha `/api` para o backend em :8080).

## Variáveis de ambiente
Veja `.env.example` (comentado). Obrigatórias no backend: `DB_URL`, `DB_USER`, `DB_PASSWORD`, `JWT_SECRET` (≥ 32 caracteres), `MFA_ENCRYPTION_KEY` (32 bytes em base64). A aplicação **não sobe** com configuração inválida.

## Antes de ir para produção
Siga `docs/product/launch-checklist.md`. Pontos que dependem do seu ambiente e **não** vêm resolvidos pela casca: HTTPS real, certificado válido no SQL Server, segredos num cofre, backup/restore testado, política de retenção da auditoria, SMTP real, monitoramento.

Veja também: [database/README.md](database/README.md) (processo dos scripts SQL) e [docs/adr](docs/adr) (decisões de arquitetura).
