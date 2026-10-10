# ADR 0002 — Sessões, tokens e proteção da conta

- **Status:** aceito (substitui as decisões de autenticação do ADR 0001)
- **Data:** 2026-10-10

## Contexto
O ADR 0001 usava um JWT único de 60 minutos guardado em `sessionStorage`. Isso deixava: token legível por qualquer XSS, logout só no navegador (o token continuava válido), nenhuma revogação, nenhum limite de tentativas e nenhuma proteção além da senha.

## Decisões
| Tema | Decisão | Motivo |
|---|---|---|
| Access token | JWT HS256 de **15 min**, só em **memória** no navegador, com `sid` (sessão), `tid` (tenant), `typ=access` | Janela curta; XSS não encontra token em storage |
| Refresh token | Opaco (256 bits), **uso único e rotativo**, em cookie `HttpOnly; SameSite=Strict; Path=/api/v1/auth` (`Secure` em HTTPS). No banco só o **hash SHA-256** | JavaScript não lê o cookie; vazamento do banco não revela tokens |
| Sessão | Tabela `sessao`; cada request autenticado confirma que a sessão não foi revogada | Logout, "sair de todos", troca/redefinição de senha e reuso de refresh derrubam o acesso **na hora** |
| Roubo de refresh | Reapresentar um refresh já usado (fora de 10 s de tolerância para abas concorrentes) revoga a sessão inteira e audita `REFRESH_REUSO` | Detecta cópia do cookie |
| CSRF | API usa `Authorization` (sem CSRF). Endpoints que usam o cookie (`refresh`, `logout`) exigem `X-Requested-With` + `SameSite=Strict` | Defesa em profundidade |
| Senhas | BCrypt; mínimo 10, máximo 72 caracteres | NIST: comprimento > regras de composição |
| Bloqueio | Tabela `login_tentativa`: 5 falhas por e-mail / 30 por IP em 15 min → bloqueio de 15 min (HTTP 429 + `Retry-After`). Vale também para e-mails inexistentes. nginx limita 10 req/s por IP em `/api/v1/auth/` | Força bruta; não revela quais contas existem |
| E-mail | Verificação obrigatória antes do login; recuperação de senha por link de uso único (1 h); respostas idênticas existindo a conta ou não | Prova de posse do e-mail; sem enumeração |
| MFA | TOTP (RFC 6238), segredo **criptografado** (AES-256-GCM, chave em `MFA_ENCRYPTION_KEY`), anti-replay por passo de tempo, 8 códigos de recuperação (hash, uso único). Login em 2 etapas com token intermediário de 5 min (`typ=mfa`, não abre rotas) | Segundo fator padrão de mercado |
| Cadastro | `REGISTRATION_ENABLED` (padrão **false** na aplicação; `true` no `.env.example` local) | Produto privado não expõe cadastro por acidente |
| Auditoria | `evento_auditoria`, só INSERT (o usuário de runtime tem `DENY UPDATE/DELETE`) | Trilha de eventos de segurança |

## Consequências e riscos residuais
- **HS256**: um segredo compartilhado. Se outros serviços precisarem validar tokens, migrar para RS256/ES256 ou IdP (Entra ID, Keycloak).
- **Perder/trocar `MFA_ENCRYPTION_KEY`** invalida o MFA já configurado. Guarde a chave em cofre e planeje rotação (re-criptografia).
- A validação de sessão faz **1 consulta por request** (índice por PK). Se virar gargalo, cachear por alguns segundos aceitando atraso equivalente na revogação.
- IP do cliente depende de `FORWARD_HEADERS_STRATEGY=native` e de o proxy sobrescrever `X-Forwarded-For`. Atrás de outro balanceador, ajuste o nginx/`internal-proxies`.
- Cadastro público ainda permite enumeração por "e-mail já cadastrado" (409), mitigada por limite por IP. Produtos sensíveis devem desligar o cadastro ou trocar por convite.
- Sem proteção contra bots (CAPTCHA) e sem notificação por e-mail de login suspeito.
