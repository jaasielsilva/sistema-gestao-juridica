# ADR 0004 — Grace period do refresh token

**Status:** Aceito  
**Data:** 2026-10-17  
**Contexto:** Auditoria de segurança SEC-003

---

## Contexto

O `SessionService` aceita um refresh token já marcado como `usado` dentro de uma janela configurável
(`app.refresh.reuse-grace-seconds`, default 10 s). O objetivo é tolerar duas abas do mesmo navegador
tentando renovar o token ao mesmo tempo com o mesmo cookie — situação legítima e comum.

O mecanismo anti-replay completo funciona assim:

- Primeira apresentação: token marcado como `usado_em = agora`.
- Apresentações **dentro** do grace period: aceitas silenciosamente (tolerância para abas concorrentes).
- Apresentações **após** o grace period: a sessão inteira é revogada (sinal de roubo).

### Risco identificado (SEC-003, CVSS 5.9)

Se um atacante obtiver o cookie `refresh_token` (vetor separado: acesso físico, MitM em HTTP,
subdomain cookie hijacking) **e** usá-lo dentro da janela de grace period, consegue criar uma sessão
paralela sem disparar a revogação.

---

## Decisão

Mantemos o grace period com as seguintes medidas de mitigação adicionais:

1. **Valor reduzido:** default alterado de 10 s para **5 s** (`REFRESH_REUSE_GRACE_SECONDS=5`).
2. **Auditoria:** todo reuso dentro do grace period gera um evento `REFRESH_REUSO_GRACE` no
   `AuditService` com nível de detalhe suficiente para detecção de anomalias.
3. **Documentação explícita:** este ADR serve como registro formal do risco aceito.

O risco residual é considerado **baixo** porque:
- Requer comprometimento prévio do cookie HttpOnly (já protegido por SameSite=Strict + Secure em produção).
- A janela de 5 s limita drasticamente a exploração prática.
- A revogação imediata continua ativa para reuso fora do grace period.

---

## Consequências

- Reduzir `REFRESH_REUSE_GRACE_SECONDS` de 10 para 5 no `application.yml` e no `.env.example`.
- Adicionar evento `REFRESH_REUSO_GRACE` em `TipoEvento`.
- Emitir auditoria em `SessionService.rotate()` quando o reuso ocorre dentro do grace period.
- Monitorar o volume de eventos `REFRESH_REUSO_GRACE` em produção; pico anômalo indica ataque.

---

## Alternativas consideradas

| Alternativa | Motivo de rejeição |
|---|---|
| Remover o grace period | Quebra UX: abas concorrentes causariam logout inesperado |
| Grace period de 0 s | Equivale a remover — mesma consequência de UX |
| Grace period > 5 s | Janela de ataque maior sem benefício proporcional de UX |
