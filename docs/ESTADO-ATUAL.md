# Estado atual da aplicação — documento oficial

| | |
|---|---|
| **Projeto** | Casca genérica de produto (template para novos produtos) |
| **Data de referência** | 2026-10-17 |
| **Versão do documento** | 1.1 |
| **Referência no código** | branch `main`, correção `8e3839f`; documentação revisada em `0b71a6e` |
| **Escopo** | Backend (Spring Boot), frontend (Angular), banco (SQL Server), infraestrutura (Docker/CI) e segurança |

> Atualizado após as correções da auditoria. O CI do commit `0b71a6e` passou nos quatro jobs, incluindo testes e build do backend e frontend, validação dos scripts SQL, build das imagens e varreduras de vulnerabilidades. A migration `009` foi aplicada no banco descartável do CI; ainda deve ser validada em homologação com dados existentes antes de produção.

---

## 1. Resumo executivo

A casca é um projeto-base para novos produtos. Inclui autenticação, multi-tenancy, auditoria, banco com permissões mínimas, Docker, CI com varredura de vulnerabilidades e uma interface Angular. As correções desta revisão estão compiladas; a execução completa dos testes e do CI para o commit atual ainda está pendente.

| Indicador | Situação |
|---|---|
| Testes e build do backend | **Passaram no CI** do commit `0b71a6e` |
| Testes e build do frontend | **Passaram no CI** do commit `0b71a6e` |
| Scripts SQL e migration `009` | **Validados no CI** em banco descartável; homologação com dados existentes pendente |
| Varredura de vulnerabilidades | Jobs Trivy e `npm audit` **passaram no CI** do commit `0b71a6e` |
| **CI no GitHub Actions** | **Verde**, 4 jobs concluídos com sucesso (run `38997980885`) |
| Pronto para produção? | **Não sozinho.** É uma base segura para construir o produto; o checklist de lançamento (`docs/product/launch-checklist.md`) lista o que depende do seu ambiente (HTTPS real, segredos em cofre, SMTP, backup etc.) |

---

## 2. Arquitetura

```
 Navegador ──HTTPS──▶ nginx (frontend, :3000 / :8443)
                        │  serve o SPA Angular
                        │  /api/*  ──proxy──▶ backend Spring Boot (:8080, só em 127.0.0.1)
                        │                         │
                        │                         ├─ SQL Server  (usuário de runtime: sem DDL)
                        │                         └─ SMTP  (Mailpit em dev)
                        └─ limite de 10 req/s por IP em /api/v1/auth/
```

- **Mesma origem**: o navegador só fala com o nginx; não há CORS em uso e o cookie de sessão é `SameSite=Strict`.
- **Monólito modular** (pacotes por módulo de negócio), sem microsserviços.
- **Schema por scripts SQL versionados** (sem Flyway/Liquibase), aplicados por `database/apply.sh` com controle de checksum.

---

## 3. Stack e versões (conforme o JAR/lockfile construídos)

| Camada | Tecnologia | Versão |
|---|---|---|
| Linguagem | Java (Temurin) | 21 |
| Framework | Spring Boot | 3.5.16 |
| Web / Segurança | Spring Framework / Spring Security | 6.2.19 / 6.5.11 |
| Persistência | Hibernate / Spring Data JPA | 6.6.53 |
| Banco | SQL Server | 2022 (driver `mssql-jdbc` 12.10.2.jre11) |
| JWT | jjwt | 0.12.6 |
| Servidor embutido / JSON | Tomcat / Jackson | 10.1.60 / 2.21.7 |
| API docs | springdoc-openapi | 2.8.9 |
| Frontend | Angular / Angular Material | 22.2.x / 22.2.2 (Material 3) |
| Linguagem frontend | TypeScript (modo estrito) | 6.0 |
| Testes | JUnit 5, Mockito, Testcontainers 1.21.3 / Vitest 5 | — |
| Infra | Docker Compose, nginx 1.31-alpine, Mailpit 1.21, GitHub Actions, Trivy | — |

---

## 4. Funcionalidades

### 4.1 Contas e autenticação
- **Cadastro** (desabilitado por padrão; controlado por `REGISTRATION_ENABLED`), com validação e senha de 10 a 72 caracteres. Cada cadastro habilitado cria um tenant; a primeira conta recebe `ADMIN` dentro dele. Papel e tenant são definidos no servidor.
- **Confirmação de e-mail obrigatória** antes do primeiro login (link de uso único, 24 h).
- **Login** com mensagem de erro única (não revela se o e-mail existe) e tempo de resposta igualado.
- **MFA (TOTP)**: configuração por QR code/chave manual, 8 códigos de recuperação (mostrados uma vez), login em duas etapas, desativação com senha + código.
- **Sessões**: access token de 15 min + refresh token rotativo (cookie `HttpOnly`); logout imediato; "sair de todos os dispositivos".
- **Recuperação de senha** por e-mail (link de uso único, 1 h) — derruba todas as sessões.
- **Troca de senha** autenticada — mantém só a sessão atual.

### 4.2 Autorização
- Papéis `USER` e `ADMIN`; `/api/v1/admin/**` só para `ADMIN`. O cadastro da primeira conta concede `ADMIN` apenas no tenant recém-criado; clientes não podem escolher papel ou tenant.

### 4.3 Multi-tenancy
- Cada usuário pertence a um tenant; o tenant vai assinado no token e isola os dados de negócio (§5, ADR 0003).
- O módulo **`nota/`** é um exemplo (apague no produto real). `USER` cria, consulta e exclui as próprias notas; `ADMIN` pode consultar e excluir notas de usuários do mesmo tenant.
- A migration `009` separa em tenants individuais as contas legadas que compartilhavam o tenant padrão, move notas e eventos de auditoria pelo usuário e revoga as sessões existentes. A migration ainda não foi aplicada a ambientes nesta revisão.
- O e-mail continua globalmente único para manter o login global atual. O cadastro duplicado responde de forma genérica; uma mesma pessoa não pode usar um único e-mail em mais de um tenant.

### 4.4 Auditoria
Eventos registrados (tabela `evento_auditoria`, só INSERT pela aplicação): cadastro, login (sucesso/falha/MFA pendente), logout, logout de todos, reuso de refresh token, e-mail verificado, pedido/conclusão de redefinição de senha, troca de senha, MFA habilitado/desabilitado/falha, uso de código de recuperação. Cada evento guarda tipo, resultado, usuário, tenant, IP, `correlationId` e detalhe (**sem senhas/tokens/segredos**). Consulta: `GET /api/v1/admin/auditoria` (ADMIN, só do próprio tenant).

### 4.5 Frontend
- Telas públicas: login (com etapa de MFA), cadastro, esqueci a senha, redefinir senha, confirmar e-mail.
- Área logada: layout com **cabeçalho**, **menu lateral** (gaveta no celular), **rodapé fixo no fim da janela**, tema claro/escuro, página **Segurança** (dados da conta, MFA, troca de senha, sair de todos os dispositivos).
- Guards de rota, interceptor (token, renovação automática em 401, `X-Requested-With`), tratamento de erros sem expor detalhes técnicos.

### 4.6 Operação
- Limpeza diária (03:30) de sessões/tokens/tentativas expirados (`HousekeepingJob`); a auditoria **não** é apagada.
- `GET /actuator/health` para monitoramento.

---

## 5. Segurança — matriz de controles

| # | Controle | Como funciona | Evidência |
|---|---|---|---|
| 1 | **Limite de tentativas** | 5 falhas por e-mail / 30 por IP em 15 min → bloqueio de 15 min (HTTP 429 + `Retry-After`), guardado no banco (vale com várias instâncias). Vale também para e-mails inexistentes. nginx: 10 req/s por IP em `/api/v1/auth/` | Testes `cincoSenhasErradasBloqueiam…`, `emailInexistenteTambemEBloqueado…`, `throttleServiceBloqueia…`; teste manual: 120 requisições paralelas → 60 × `200` e 60 × `429` |
| 2 | **Logout real e refresh** | Sessão no banco; cada request confere se ela está ativa; refresh de uso único e rotativo; reuso = sessão revogada | `logoutInvalidaOAccessTokenNaHora…`, `logoutAllDerruba…`, `refreshRotacionaOTokenEReusoDoAntigoRevoga…`, `esqueciASenhaRedefineDerrubaSessoes…`, `trocarSenhaMantemSoASessaoAtual` |
| 3 | **Token fora do alcance do JavaScript** | Access token só em memória; refresh em cookie `HttpOnly; SameSite=Strict` | Teste manual no navegador: `localStorage` só tem `app.theme`, `sessionStorage` vazio, `document.cookie` vazio; sessão restaurada após recarregar; teste unitário confirma ausência de storage |
| 4 | **Recuperação de senha, e-mail verificado, MFA, cadastro controlável** | Ver §4.1 | `cadastroExigeVerificacaoDeEmail…`, `esqueciASenha…` (incl. e-mail inexistente = resposta idêntica, sem envio), `mfaLoginExige…`, `tokenMfaNaoAbreRotasProtegidas`, `codigoDeRecuperacaoFuncionaUmaUnicaVez`, `desativarMfaExigeSenhaECodigo`, `RegistrationDisabledIntegrationTest`; TOTP validado contra os vetores da RFC 6238 (`TotpServiceTest`) |
| 5 | **HTTPS e HSTS** | Override `docker-compose.https.yml`: nginx com TLS 1.2/1.3, redirecionamento HTTP→HTTPS, `Strict-Transport-Security` (2 anos), cookie `Secure` | Verificado com `curl` (cabeçalho HSTS, `Set-Cookie … Secure; HttpOnly; SameSite=Strict`, redirecionamento 301) usando certificado autoassinado |
| 6 | **Banco com menor privilégio** | A aplicação usa `app_runtime` (`db_datareader` + `db_datawriter`, sem DDL); `DENY UPDATE/DELETE` na auditoria e `DENY` de escrita em `schema_version`. O `sa` só roda o `apply.sh` | Verificado com `sqlcmd` como `app_runtime`: `DELETE/UPDATE` na auditoria, `UPDATE` em `schema_version`, `CREATE TABLE`, `DROP TABLE` → todos negados; leitura funciona; a aplicação sobe e opera nesse usuário |
| 7 | **Auditoria** | Ver §4.4 | `auditoriaRegistraFalhasDeLoginComEmailEIpMasSemSenha`, `areaAdminEAuditoriaSoParaAdminESoDoProprioTenant` |
| 8 | **Varredura de dependências** | Trivy no JAR (todas as bibliotecas, inclusive transitivas) e nas imagens + `npm audit` no CI e toda segunda-feira; Dependabot semanal | Execução local (§11) |
| 9 | **Multi-tenancy e posse** | `@TenantId` + `TenantScopedRepository`; cadastro cria tenant independente; endpoints de notas conferem posse (ADMIN pode ver notas do próprio tenant) | Testes de isolamento e posse atualizados; execução após as mudanças ainda pendente |

### Outros controles já ativos
- **Senhas** em BCrypt; tokens (refresh, e-mail, recuperação, códigos do MFA) guardados só como **hash SHA-256**; segredo TOTP **criptografado** (AES-256-GCM) com chave fora do banco.
- **JWT**: assinatura, emissor, validade e tipo (`access` ≠ `mfa`) validados; token adulterado, expirado, de outro emissor ou de outra chave é rejeitado.
- **Respostas de erro** uniformes (`ApiError`) sem stack trace/SQL; `X-Correlation-Id` em toda resposta.
- **Cabeçalhos**: CSP restritiva (sem script inline), `X-Content-Type-Options`, `X-Frame-Options: DENY`, `Referrer-Policy`, `Permissions-Policy`, `Cache-Control: no-store` na API.
- **Proteção CSRF** nos endpoints com cookie: `X-Requested-With` obrigatório + `SameSite=Strict`.
- **Configuração validada na partida**: `JWT_SECRET` < 32 caracteres ou `MFA_ENCRYPTION_KEY` inválida → a aplicação **não sobe**.
- **Imagens Docker**: usuário sem privilégios (backend), `apt/apk upgrade` na construção, binário `pebble` não usado removido.
- **Portas** do Docker presas a `127.0.0.1`.
- **Segredos**: nenhum no repositório; `.env` fora do Git; `.env.example` só com marcadores "troque".

---

## 6. Status dos 9 itens solicitados

| Item | Status |
|---|---|
| 1. Limite de tentativas de login | ✅ Implementado e testado |
| 2. Logout real e refresh token | ✅ Implementado e testado |
| 3. Token fora do `sessionStorage` (cookie HttpOnly) | ✅ Implementado e verificado no navegador |
| 4. Recuperação de senha, verificação de e-mail, MFA, cadastro controlável | ✅ Implementado e testado (MFA validado com o padrão RFC 6238; **não** testado com app autenticador real em celular — §13) |
| 5. HTTPS / HSTS | ✅ Implementado; verificado com certificado autoassinado. Certificado **real** é responsabilidade do seu ambiente |
| 6. Banco com usuário de menor privilégio | ✅ Implementado e verificado. `trustServerCertificate` permanece `true` **apenas** no Docker local; produção exige certificado válido (checklist) |
| 7. Auditoria de eventos | ✅ Implementada e testada |
| 8. Varredura de dependências no CI | ✅ Implementada (Trivy JAR/imagens, `npm audit`, Dependabot) e **executada com sucesso no GitHub Actions**. (As duas primeiras execuções falharam por um erro do instalador da *action* do Trivy — não por vulnerabilidade; trocado pelo contêiner oficial do Trivy, na versão validada localmente) |
| 9. Multi-tenancy e posse | ✅ Correções implementadas; testes atualizados, mas ainda não executados após esta revisão |

---

## 7. Modelo de dados

Scripts em `database/scripts/` (ver `database/README.md`):

| Script | Tabelas / mudanças |
|---|---|
| 000 | `schema_version` (controle de scripts; só `apply.sh`) |
| 001 | `usuario` |
| 002 | `tenant`; `usuario.tenant_id` (usuários antigos → tenant padrão) |
| 003 | `usuario.email_verificado` (antigos = verificados), `mfa_ativo`, `mfa_segredo`, `mfa_ultimo_passo` |
| 004 | `sessao`, `refresh_token` |
| 005 | `token_acao`, `mfa_recovery_code` |
| 006 | `login_tentativa` |
| 007 | `evento_auditoria` |
| 008 | `nota` (**exemplo** de entidade com tenant) |
| 009 | Provisiona sequência de tenants, separa usuários legados e revoga suas sessões |
| 010 | `cliente` (pessoa física/jurídica, CPF/CNPJ único por tenant, inativação em vez de exclusão) |

A migrações `002`–`008` foram aplicadas com sucesso em revisão anterior sobre um banco com dados. A migration `009` inclui alterações de dados e revogação de sessões; faça backup e valide-a em homologação antes de aplicar em produção.

---

## 8. API (`/api/v1`)

| Método e caminho | Acesso | Função |
|---|---|---|
| `GET /auth/config` | público | Informa se o cadastro está habilitado |
| `POST /auth/register` | público* | Cadastro (*se habilitado) |
| `POST /auth/login` | público | Login (devolve token ou desafio MFA) |
| `POST /auth/mfa/verify` | público (token MFA) | Conclui o login com MFA |
| `POST /auth/refresh` | cookie + `X-Requested-With` | Renova o access token |
| `POST /auth/logout` | cookie/token + `X-Requested-With` | Encerra a sessão |
| `POST /auth/verify-email`, `/resend-verification` | público | Confirmação de e-mail |
| `POST /auth/forgot-password`, `/reset-password` | público | Recuperação de senha |
| `GET /auth/me` | autenticado | Dados do usuário |
| `POST /auth/logout-all`, `/change-password` | autenticado | Sessões e senha |
| `POST /auth/mfa/setup`, `/enable`, `/disable` | autenticado | MFA |
| `GET/POST /notas`, `GET/DELETE /notas/{id}` | autenticado | **Exemplo** com tenant |
| `GET /clientes` (`busca`, `tipo`, `ativo`, `page`, `size`), `GET /clientes/{id}`, `POST /clientes`, `PUT /clientes/{id}` | autenticado | Cadastro de clientes (isolado por tenant) |
| `POST /clientes/{id}/inativar`, `/reativar` | `ADMIN` | Altera a situação do cliente |
| `GET /admin/auditoria` | `ADMIN` | Trilha de auditoria (paginada) |
| `GET /actuator/health` | público | Saúde |

Swagger só com `SWAGGER_ENABLED=true`.

---

## 9. Configuração (variáveis de ambiente)

Obrigatórias no backend: `DB_URL`, `DB_USER`, `DB_PASSWORD`, `JWT_SECRET` (≥ 32), `MFA_ENCRYPTION_KEY` (32 bytes base64). Demais (com padrões seguros): `APP_BASE_URL`, `REGISTRATION_ENABLED` (padrão **false** na aplicação), `COOKIE_SECURE` (padrão **true**), `JWT_ACCESS_MINUTES` (15), `REFRESH_DAYS` (7), `REFRESH_REUSE_GRACE_SECONDS` (10), `THROTTLE_*`, `FORWARD_HEADERS_STRATEGY`, `CORS_ALLOWED_ORIGINS`, `SWAGGER_ENABLED`, `SPRING_MAIL_*`, `MAIL_FROM`. No Docker: `APP_DB_USER`, `APP_DB_PASSWORD`, `DB_NAME`, `DB_TRUST_SERVER_CERT`. Detalhes comentados em `.env.example`.

---

## 10. Qualidade e verificações realizadas

### 10.1 Testes automatizados
| Suíte | Testes | Cobre |
|---|---|---|
| `JwtServiceTest` | 7 | emissão/validação, adulteração, expiração, emissor, tipo access×mfa |
| `AuthServiceTest` | 7 | regras de cadastro/login com dependências simuladas |
| `TotpServiceTest` | 7 | vetores da RFC 6238, janela, replay, Base32, URI `otpauth` |
| `SecretCipherTest` | 4 | AES-GCM, IV aleatório, adulteração, chave inválida |
| `AuthIntegrationTest` | 24 | fluxos completos em SQL Server real (§5) |
| `RegistrationDisabledIntegrationTest` | 1 | cadastro desligado |
| Frontend (5 arquivos) | 24 | serviço de autenticação, interceptor (renovação única em 401), guards, login (MFA, reenvio), cadastro |

O banco de teste é criado a partir dos **scripts reais** e o Hibernate roda com `validate`: se uma entidade divergir do SQL, os testes falham.

### 10.2 Verificações manuais (ambiente Docker)
- Migração `002`–`008` sobre banco com dados; `apply.sh` idempotente (segunda execução ignora scripts já aplicados).
- Conta antiga continuou entrando (verificada por padrão na migração).
- Navegador: cadastro pela tela → e-mail no Mailpit → login bloqueado antes da confirmação → confirmação → login → recarregar mantém a sessão → ativar MFA (QR code/chave) → logout → login pede o código → **código reutilizado recusado (replay)** → código do passo seguinte aceito.
- Layout em tela larga (menu fixo) e estreita (gaveta); rodapé sempre no fim da janela.
- Permissões do `app_runtime`, HTTPS/HSTS e limite do nginx (§5).

---

## 11. Vulnerabilidades (varredura de 2026-10-10, Trivy 0.58.0)

Comandos reproduzíveis estão no `CLAUDE.md`/CI. Resultado final: **0 HIGH/CRITICAL** em JAR, imagens (backend, frontend) e `package-lock.json`, **exceto** as 3 exceções abaixo, registradas em `.trivyignore.yaml` com justificativa e **validade até 2027-01-10** (depois disso o CI volta a falhar, forçando reavaliação).

**O que a varredura encontrou e foi corrigido**
- `mssql-jdbc` 12.6.4 → 12.10.2.jre11; Spring Boot 3.3.5 → 3.5.16; Jackson → 2.21.7; Tomcat → 10.1.60; micrometer/Spring atualizados pela BOM do Boot.
- Imagem do nginx desatualizada (43 achados) → `nginx:1.31-alpine` + `apk upgrade`; backend: `apt upgrade` + remoção do binário `pebble` não usado.
- Observação: a varredura só do `pom.xml` **não** enxerga as versões gerenciadas pelo Spring Boot; por isso o CI varre o **JAR final**.

**Exceções aceitas (3)**
| CVE | Motivo |
|---|---|
| CVE-2025-59250 (`mssql-jdbc`) | **Falso positivo**: usamos exatamente a versão corrigida (`12.10.2.jre11`); o Trivy lê a versão sem o sufixo |
| CVE-2026-47884 (`spring-webmvc`, crítica) | Só afeta `XsltView`; a API só devolve JSON. Correção só existe no Spring Framework 7 (Boot 4) |
| CVE-2026-47890 (`spring-webmvc`, crítica) | Só afeta SSE com *view fragments*; a API não usa SSE nem views. Idem |

**Risco estrutural**: o suporte de código aberto do Spring Boot 3.5 / Spring Framework 6.2 terminou em 2026. Correções futuras podem exigir a **migração para o Spring Boot 4** (mudanças relevantes: Spring 7, Jackson 3, Hibernate 7, Security 7). Recomenda-se planejá-la.

---

## 12. Limitações e riscos residuais

**Segurança**
- **Administrador do banco** ainda pode alterar a auditoria. A casca só impede alteração pelo usuário da aplicação; imutabilidade real exige WORM/SIEM/log externo.
- **Multi-tenancy é lógico** (mesmo banco/tabelas): não protege contra DBA nem contra SQL nativo que esqueça `tenant_id`. Regulações rígidas podem pedir banco por cliente.
- **XSS** continua sendo o maior risco de front-end: o access token (15 min) está em memória e o refresh é `HttpOnly`, mas um XSS ainda poderia agir *como* o usuário enquanto a página está aberta. Mitigado por CSP sem script inline e ausência de HTML dinâmico; manter essa disciplina.
- **HS256** (segredo compartilhado). Se outros serviços precisarem validar tokens, migrar para RS256/ES256 ou IdP.
- **`MFA_ENCRYPTION_KEY`**: perder/trocar invalida os MFAs configurados; não há rotação automática de chave.
- **Cadastro público** permite enumeração por "e-mail já cadastrado" (mitigada por limite por IP); produtos sensíveis devem desligar o cadastro ou usar convite.
- Sem CAPTCHA/anti-bot, sem aviso por e-mail de login suspeito, sem política de expiração/histórico de senhas, sem exigência de MFA para ADMIN, sem verificação de senhas vazadas.
- Cada request autenticado faz **1 consulta** de sessão (índice por PK) — escala bem, mas é um custo.
- O IP registrado depende do proxy: **no Docker Desktop local aparece o gateway** (`172.x.0.1`), não o IP real. Em produção, o proxy/balanceador precisa repassar `X-Forwarded-For` corretamente.
- O redirecionamento HTTP→HTTPS do override assume a porta 443 (produção); localmente acesse `https://localhost:8443` direto.

**Produto/funcional**
- Sem criação de tenants nem convite de usuários (auto-cadastro cai no tenant padrão).
- Sem tela administrativa (auditoria só pela API); sem gestão de usuários/papéis pela interface (promoção a `ADMIN` é por SQL).
- Papéis fixos `USER`/`ADMIN` (sem permissões granulares).
- Jobs/tarefas assíncronas **não** carregam tenant automaticamente (devem receber e definir o `TenantContext`).
- O módulo `nota/` é exemplo e deve ser removido no produto real (script `008` incluso).

---

## 13. O que NÃO foi verificado

- **Aplicativo autenticador real** (Google/Microsoft Authenticator no celular): o TOTP foi validado pelos vetores da RFC 6238 e por um gerador independente, não por um app real.
- **E-mail real**: só Mailpit (SMTP de testes). Entregabilidade, SPF/DKIM/DMARC e provedores reais não foram testados.
- **HTTPS no navegador**: validado com `curl` e certificado autoassinado; o navegador não foi usado nesse modo.
- **Execução agendada do CI** (toda segunda, 09:00 UTC): configurada, mas ainda não houve uma execução agendada. O Trivy do CI usa a versão fixa 0.58.0 (precisa ser atualizada manualmente de tempos em tempos).
- **Proteção de branch** no GitHub (exigir CI verde antes de mesclar): não configurada.
- **`HousekeepingJob`** (limpeza diária): sem teste automatizado.
- **Frontend**: sem testes automatizados para as telas *esqueci/redefinir senha*, *confirmar e-mail* e *Segurança* (o fluxo foi exercitado manualmente no navegador).
- **Carga/desempenho**, **teste de intrusão** e **revisão de segurança independente**: não realizados.
- **Rodar contra o SQL Server local do usuário** (`GENERICOPRIVILEGIADO`): não executado (exigiria credenciais); há passo a passo no `README.md`.
- Imagens/dependências foram varridas **hoje**; novas CVEs surgirão — por isso o CI roda toda segunda-feira.

---

## 14. Operação rápida

```bash
cp .env.example .env            # troque DB_PASSWORD, APP_DB_PASSWORD, JWT_SECRET, MFA_ENCRYPTION_KEY
docker compose up --build       # app :3000 · API :8080 · e-mails de teste :8025
cd backend  && mvn verify                  # 50 testes (exige Docker)
cd frontend && npx ng test --watch=false   # 24 testes
```
Novo produto: `scripts/init-project.ps1` renomeia pacote/artefato; depois preencha `CLAUDE.md` e `docs/product/PRD.md`, remova o módulo `nota/` e siga o `launch-checklist.md`.

---

## 15. Próximos passos recomendados (ordem sugerida)

1. Ligar a proteção de branch no GitHub exigindo CI verde; atualizar a versão fixa do Trivy no `ci.yml` periodicamente.
2. Planejar a migração para **Spring Boot 4** (remove as exceções do §11).
3. Testar o MFA com um aplicativo real e o fluxo de e-mail com SMTP real.
4. Decidir, por produto: cadastro aberto × convite; MFA obrigatório para `ADMIN`; criação de tenants.
5. Telas administrativas (usuários, papéis, auditoria) e testes do frontend restantes.
6. Auditoria em armazenamento externo imutável, se houver requisito regulatório.
7. Revisão de segurança independente / teste de intrusão antes de abrir ao público.

---

## 16. Estrutura do repositório

```
backend/            API Spring Boot (auth/, user/, security/, tenant/, audit/, mail/, shared/, nota/ [exemplo])
frontend/           Angular (core/, layout/, features/, shared/) + nginx (nginx/*.conf) + Dockerfile
database/           scripts/ (000–008), post/grants.sql, apply.sh, README.md
docs/               ESTADO-ATUAL.md (este), adr/ (0001–0003), product/ (PRD, checklist de lançamento)
infrastructure/https/  gerador de certificado autoassinado (teste local)
scripts/            init-project.ps1
.github/            workflows/ci.yml, dependabot.yml
docker-compose.yml · docker-compose.https.yml · .env.example · .trivyignore.yaml · CLAUDE.md · README.md
```
