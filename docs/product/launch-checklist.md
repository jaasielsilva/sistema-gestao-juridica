# Checklist de lançamento

Legenda: ✅ a casca já entrega · 🔧 você precisa configurar no seu ambiente · ⬜ decisão/ação do produto.

## Segurança
- ✅ Senhas com BCrypt, sessões revogáveis, refresh rotativo em cookie HttpOnly, bloqueio por tentativas, MFA, auditoria
- ✅ Usuário de banco de runtime com permissões mínimas (sem DDL; auditoria só INSERT)
- ✅ Cabeçalhos de segurança + CSP; HTTPS/HSTS prontos (`docker-compose.https.yml`)
- ✅ Varredura de vulnerabilidades (Trivy no JAR e nas imagens, `npm audit`) e Dependabot
- 🔧 `JWT_SECRET` aleatório (≥ 48 chars), diferente por ambiente, num cofre/secret manager
- 🔧 `MFA_ENCRYPTION_KEY` (`openssl rand -base64 32`) num cofre, **com backup** (perdê-la invalida os MFAs)
- 🔧 `DB_PASSWORD` (admin) e `APP_DB_PASSWORD` fortes e diferentes; só o `apply.sh` usa o admin
- 🔧 `DB_TRUST_SERVER_CERT=false` + certificado válido no SQL Server
- 🔧 HTTPS real na frente (certificado de verdade) e `COOKIE_SECURE=true`
- 🔧 `SWAGGER_ENABLED=false` em produção (ou protegido)
- 🔧 `CORS_ALLOWED_ORIGINS` e `APP_BASE_URL` com os domínios reais
- 🔧 SMTP real (`SPRING_MAIL_HOST/PORT/USERNAME/PASSWORD`), com SPF/DKIM/DMARC no domínio de envio
- 🔧 Rate limit/WAF na borda se houver tráfego público (o nginx já limita `/api/v1/auth/`)
- ⬜ `REGISTRATION_ENABLED=false` se o produto for privado/por convite
- ⬜ Exigir MFA para administradores (a casca oferece MFA opcional)
- ⬜ Nenhum segredo no repositório (`git log -p | grep -iE "secret|password"`) e `.env` fora do Git

## Dados
- 🔧 Backup automático do SQL Server e **restauração testada**
- 🔧 Scripts aplicados com `apply.sh` por usuário de migração; `schema_version` conferida entre ambientes
- ⬜ Política de retenção da auditoria e LGPD (base legal, exclusão/anonimização sob pedido, e-mail/IP na auditoria são dados pessoais)
- ⬜ Se houver um administrador de banco não confiável: auditoria em armazenamento WORM/SIEM (a casca só impede alteração pelo usuário da aplicação)

## Multi-tenancy (se o produto for SaaS)
- ✅ Isolamento automático por tenant com testes negativos (módulos `cliente/` e `nota/`)
- ⬜ Fluxo de criação de tenants e convite de usuários (hoje o auto-cadastro cai no tenant padrão)
- ⬜ Teste de isolamento para **cada** módulo novo (`cliente/` já tem)
- ⬜ Validar a migration `010` (clientes) e medir desempenho da listagem com a carga de `database/seed`

## Operação
- 🔧 Health check (`/actuator/health`) monitorado
- 🔧 Logs centralizados, sem senha/token; busca por `X-Correlation-Id`
- 🔧 Alertas de erro 5xx, latência, picos de `LOGIN_FALHA`/`REFRESH_REUSO` na auditoria
- 🔧 Plano de rollback da versão e do banco
- ✅ CI (testes + varredura) rodando a cada PR e toda segunda-feira
- ⬜ Revisar `.trivyignore.yaml` antes das datas de expiração

## Produto
- ⬜ Fluxos do PRD testados ponta a ponta
- ⬜ Termos de uso e política de privacidade publicados
- ⬜ Domínio, e-mail transacional e suporte definidos
