# ADR 0006 — `unsafe-inline` em `style-src-attr` da CSP

**Status:** Aceito
**Data:** 2026-11-08
**Contexto:** Auditoria de segurança SEC-006

---

## Contexto

A Content-Security-Policy em `frontend/nginx/security-headers.conf` restringe `style-src` e
`style-src-elem` a `'self'` + nonce por resposta, mas mantém `style-src-attr 'unsafe-inline'`.

Levantamento no código da aplicação (`frontend/src/app`) não encontrou nenhum uso de `[style]` ou
`ngStyle` — ou seja, a aplicação em si não gera estilo inline. A origem do `unsafe-inline` é o
**Angular CDK** (usado pelo Angular Material): overlays de menu, tooltip, autocomplete e dropdown
posicionam seus painéis escrevendo `style="position: ...; top: ...px; left: ...px; transform: ...`
diretamente no atributo `style` do elemento, em tempo de execução, conforme o usuário interage com a
tela. Esse valor muda a cada abertura/posição, então não é possível substituir por uma classe CSS
estática nem por um hash (`'sha256-...'`) fixo na CSP — o hash mudaria a cada render.

## Decisão

Mantemos `style-src-attr 'unsafe-inline'`, isolado das demais diretivas de estilo (que já usam nonce),
e com as seguintes mitigações:

1. **Escopo mínimo:** o `unsafe-inline` vale só para atributos `style="..."`, nunca para `<style>`
   (coberto por `style-src-elem` com nonce) nem para `script-src` (que é `'none'` por padrão, já
   que `default-src 'none'` do CSP geral não inclui `'unsafe-inline'` em script em nenhum lugar).
2. **Sem vetor de execução de JS:** um atacante que injete HTML/CSS via `style="..."` não consegue
   executar JavaScript por essa via (diferente de um `unsafe-inline` em `script-src`). O risco
   residual é de CSS injection (ex.: exfiltração de dados por seletores CSS avançados), não XSS.
3. **Documentação explícita:** este ADR registra a causa raiz e o porquê de não ser corrigível sem
   trocar a biblioteca de UI.

O risco residual é considerado **informativo/baixo** porque:
- Não há vetor de execução de script via este atributo.
- Nenhum código da aplicação usa esse mecanismo — é só o CDK, já confiável (dependência oficial do
  Angular, mantida pelo Google, com Dependabot cobrindo atualizações de `npm`).
- `style-src` e `style-src-elem` continuam restritos a nonce, cobrindo a superfície real de CSS
  carregado via `<style>`/`<link>`.

---

## Consequências

- Nenhuma mudança de código necessária.
- Reavaliar este ADR se a aplicação passar a usar `[style]`/`ngStyle` próprio (nesse caso, migrar
  para classes estáticas antes, para não ampliar o uso do `unsafe-inline`).
- Reavaliar se o Angular CDK adotar nonce-aware style injection em versão futura (acompanhar via
  changelog do Angular Material).

---

## Alternativas consideradas

| Alternativa | Motivo de rejeição |
|---|---|
| Remover `unsafe-inline` de `style-src-attr` | Quebra overlays do Angular Material (menu, tooltip, autocomplete, dropdown) — toda a UI baseada em CDK para de posicionar corretamente |
| Hash estático por valor de estilo | Inviável: o valor de `style=` muda a cada posição/abertura em runtime |
| Trocar Angular Material por outra lib de UI | Custo desproporcional ao risco (CSS injection sem execução de script); fora de escopo desta auditoria |
