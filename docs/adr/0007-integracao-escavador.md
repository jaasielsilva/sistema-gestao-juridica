# ADR 0007 — Integração com o Escavador (movimentações processuais)

**Status:** Proposto
**Data:** 2026-11-08
**Contexto:** novo módulo, requisito de produto (monitoramento automático de processos)

---

## Contexto

O Escavador é uma API paga (cobrança por requisição/serviço, crédito pré-pago com validade — no
caso deste cliente, R$200 a cada 3 meses, "usa ou perde") que fornece dados públicos de processos
judiciais: localização de processos por OAB, movimentações/publicações por número CNJ, capa do
processo (partes, vara, classe, valor), resumo por IA, PDFs de documentos públicos e **monitoramento
contínuo com callback**.

Já existe uma integração equivalente no LexCRM (outro produto do mesmo portfólio). Uma varredura
dessa integração (ver achados abaixo) identificou três problemas estruturais que este ADR existe
para **evitar explicitamente** aqui, porque este sistema é uma plataforma multi-tenant vendida a
múltiplos escritórios — um problema de isolamento ou de custo afeta todos os clientes ao mesmo tempo:

| Anti-padrão encontrado no LexCRM | Por que não serve aqui |
|---|---|
| Token único global (`ESCAVADOR_API_TOKEN` em env var), compartilhado por todos os clientes | Um SaaS multi-tenant precisa de crédito e billing por escritório; um token global significa que o uso de um tenant consome o crédito de todos. |
| SSRF confirmada (CVSS 8.5): parâmetro de paginação da resposta do Escavador é reusado como URL literal da próxima chamada autenticada, sem validação de host | Vaza o token para qualquer host que um usuário autenticado (ou a própria API, se comprometida) decida apontar. |
| Nenhum limite de gasto por usuário/tenant numa API cobrada por chamada | DoS financeiro: um cliente (ou um usuário malicioso dentro de um cliente) pode esgotar o orçamento do mês em minutos. |
| LexCRM usa só polling client-driven, assumindo que não existe webhook | **Incorreto para este projeto**: a doc oficial da API V1 (`https://api.escavador.com/v1/docs/monitoramento-no-site-do-tribunal`) confirma callback real por evento (`movimentacao_nova`, `processo_dado_novo`, `envolvido_novo`, `update_status`). O LexCRM simplesmente não usa esse recurso — não é limitação da API. |

### Decisão de billing já tomada (fora deste ADR, decisão do usuário)

Cada escritório-cliente compra o próprio crédito Escavador e cadastra a própria chave (Personal
Access Token, Bearer) — **modelo B** da análise original, não um token único da plataforma. Isso
bate com o desenho de credencial por tenant abaixo. O primeiro token de produção já foi criado
("Sistema de Gestão Jurídica - PRODUÇÃO") e testado com sucesso em 2026-11-09 (ver seção de
validação).

### Preço real dos serviços (tabela de preços da conta, API V1, consultada em 2026-11-08)

| Serviço | Preço |
|---|---|
| Monitoramento de processo — diário | R$ 1,76 / mês por processo |
| Monitoramento de processo — semanal | R$ 0,32 / mês por processo |
| Monitoramento de processo — mensal | R$ 0,08 / mês por processo |
| Monitoramento diário + documentos públicos | R$ 2,30 / mês |
| Monitoramento semanal + documentos públicos | R$ 0,55 / mês |
| Monitoramento mensal + documentos públicos | R$ 0,18 / mês |
| Monitoramento de novos processos (por termo/nome/CPF/CNPJ) | R$ 2,20 / mês (até 200 itens + R$0,05/200) |
| Atualização manual do processo no tribunal ("atualizar agora") | R$ 0,10 por chamada |
| Atualização manual + documentos específicos dos autos | R$ 0,75 |
| Atualização manual + autos completos | R$ 1,50 |
| Atualização manual + documentos públicos | R$ 0,20 |
| Atualizar resumo do processo por IA | R$ 0,08 |
| Ler resumo por IA já existente | R$ 0,05 |
| Movimentações do processo por CNJ (sem forçar atualização) | R$ 0,05 |
| Capa do processo / envolvidos do processo | R$ 0,05 |
| Processos do advogado por OAB | R$ 4,50 (até 200 itens + R$0,05/200) |
| Estatísticas de processos de um envolvido | R$ 3,00 (até 1000 itens + R$0,05/1000) |

Com o orçamento de R$200/3 meses (~R$66,67/mês), monitoramento **diário** em todos os processos só
cobre ~37 processos/mês; **semanal** cobre ~208; **mensal** cobre ~833. Conclusão: a frequência de
monitoramento **não pode ser fixa para toda a base** — precisa ser escolhida por processo (e,
futuramente, controlada por plano comercial).

### Validação end-to-end já realizada (2026-11-09)

Teste manual via `curl` direto contra a API, fora do sistema (nenhum código nosso envolvido ainda):
- `POST /api/v1/monitoramentos-tribunal` com CNJ real (`0004523-86.2024.8.26.0625`), `frequencia: MENSAL`
  → `{"id":4544414,"monitor":{"status":"FOUND", ...}}`. Confirma que o cadastro funciona e custa R$0,08/mês.
- `PUT /api/v1/monitoramentos-tribunal/4544414` com `frequencia: DIARIA` → `{"status":"success"}`.
  Confirma que a frequência é editável sem recriar o monitoramento.
- Callback configurado para uma URL temporária (webhook.site) — aguardando primeiro evento real
  para validar o payload de callback (pendente, ver "Não decidido").

---

## Decisão

### 1. Módulo isolado
Novo pacote `backend/.../integracao/escavador/`, seguindo a mesma convenção de `auth/`, `tenant/`:
- `EscavadorClient` — HTTP client **fechado**: base URL fixa (`https://api.escavador.com`), um método
  por endpoint, parâmetros tipados. Nunca aceita URL de input do usuário/frontend como destino de
  chamada autenticada (isso é o que causou a SSRF no LexCRM — o parâmetro de paginação vindo do
  **cliente** era usado como URL literal). Seguir o link `next` devolvido pela própria resposta do
  Escavador é esperado e documentado pela API (contrato de paginação por página/cursor definido por
  rota); a defesa real é validar que toda URL usada numa chamada autenticada resolve para o host
  `api.escavador.com`, nunca aceitar uma URL completa vinda do usuário final.
- `EscavadorService` — regra de negócio: decide se a chamada é permitida (cota em R$, não em
  contagem simples — ver seção 3), monta a chamada, normaliza a resposta, persiste.
- `EscavadorController` — endpoints administrativos do tenant (configurar chave, ver uso/saldo,
  habilitar monitoramento por processo e escolher a frequência). `@PreAuthorize("hasRole('ADMIN')")`.
- `EscavadorCallbackController` — endpoint público (`POST /api/v1/integracoes/escavador/callback/{tenantPublicId}`),
  **sem autenticação de usuário** (é o Escavador chamando, não um usuário logado), mas validado pelo
  token de callback configurado na conta daquele tenant (ver seção 2). Recebe os eventos
  `movimentacao_nova`, `processo_dado_novo`, `envolvido_novo`, `update_status` e os resultados de
  `solicitar-atualizacao` (V2). Substitui o job de polling que eu tinha desenhado antes do teste
  contra a doc real — **o Escavador quem avisa a gente**, não o contrário.

### 2. Credencial por tenant, nunca global
Nova entidade `TenantScopedEntity`: `EscavadorConfiguracao` — um registro por tenant:
- `tokenCifrado` (AES-256-GCM, reaproveitando `SecretCipher` já usado no MFA — mesma chave de
  infraestrutura, `app.escavador.encryption-key`, variável de ambiente própria).
- `callbackSecretCifrado` — o token de validação de callback que o **próprio tenant** gera no painel
  Escavador dele, guardado cifrado do mesmo jeito. Todo callback recebido em
  `EscavadorCallbackController` tem o header `Authorization` comparado (tempo constante,
  `MessageDigest.isEqual`, mesmo padrão do `TotpService`) contra esse valor antes de processar
  qualquer coisa — sem isso, qualquer um que descobrisse a URL do callback poderia injetar eventos
  falsos de "nova movimentação" num tenant.
- `habilitado` (boolean).
- `saldoLimiteReais` (configurável por plano comercial — em reais, não em "número de chamadas",
  porque o custo real varia por tipo de serviço, ver tabela de preços).
- Cada tenant usa o **próprio** crédito Escavador (token e callback secret do próprio painel dele).
  Nada disso aparece em log, resposta de API ou auditoria (mascarado, mesmo padrão do relatório de
  auditoria de segurança).

### 3. Cota em reais aplicada no servidor, antes da chamada paga
Nova entidade `TenantScopedEntity`: `EscavadorUso` — contador mensal (tenant_id, ano_mes,
valor_gasto_centavos). `EscavadorService` verifica `uso.valorGasto + custoDaChamada <=
configuracao.saldoLimiteReais` **antes** de qualquer chamada paga (busca por OAB, resumo IA,
atualização forçada, ativação de monitoramento); se exceder, retorna erro de negócio
(`BusinessException.conflict`) em vez de silenciosamente gastar além do limite. O custo de cada
serviço é uma tabela estática no código (replicando a tabela de preços da seção de Contexto),
revisada manualmente se o Escavador mudar os valores. Isso fecha a classe de problema do SEC-027 do
LexCRM (DoS financeiro) por construção, não por auditoria a posteriori.

### 4. Dados normalizados, ligados ao Processo existente
Nova entidade `TenantScopedEntity`: `Movimentacao` (processo_id FK, data, descricao, tipo, origem
`ESCAVADOR`, hash de dedup do payload do evento). Vinculada ao `Processo` já existente — sem
duplicar o conceito de processo, sem reinventar estrutura que já existe no módulo `processo/`. Dedup
por hash evita gravar duas vezes o mesmo evento se o Escavador reenviar um callback (prática que a
própria doc recomenda tratar como idempotente, já que reentrega é esperada).

Campo novo em `Processo`: `monitoramentoEscavadorId` (id do monitoramento na conta do tenant,
nullable) e `frequenciaMonitoramento` (DIARIA/SEMANAL/MENSAL/nenhum) — permite habilitar/desabilitar
e trocar frequência por processo individualmente, batendo com a realidade de custo por frequência.

### 5. Toda chamada é auditada via `AuditService`
Diferente do LexCRM (tabela paralela `EscavadorChamadaPaga`), aqui a chamada passa pelo mecanismo de
auditoria já existente no projeto (`AuditService`), com tipo de evento novo (`ESCAVADOR_CHAMADA_PAGA`
e `ESCAVADOR_CALLBACK_RECEBIDO`), nunca registrando o token/callback secret no detalhe (regra 10 do
CLAUDE.md).

### 6. Callback real (webhook), não polling
Confirmado contra a doc oficial e validado com chamada real (ver seção de Contexto): o monitoramento
é registrado uma vez (`POST /monitoramentos-tribunal`) e o próprio Escavador avisa via callback
quando encontra algo novo. Não precisamos de job de polling para isso. Resta um job leve de
**reconciliação** (ex.: diário, de baixo custo): verificar se monitoramentos marcados como ativos no
nosso banco ainda existem/estão corretos na conta do Escavador — cobre o caso de alguém desativar um
monitoramento direto no painel do Escavador sem passar pelo nosso sistema.

---

## Valor que isso agrega ao produto (SaaS)

- **Diferencial comercial**: monitoramento automático de movimentações processuais é um recurso de
  alto valor percebido para escritórios — elimina checagem manual diária no site do tribunal.
- **Modelo de monetização direto**: `limiteChamadasMes` por tenant permite vender como add-on por
  plano (ex.: Básico sem integração, Pro com N consultas/mês, Enterprise com monitoramento ilimitado
  de todos os processos). O contador de uso já fica pronto pra aparecer numa tela de billing.
  Monetização mais detalhada ainda não desenhada aqui — ver seção "Não decidido".
- **Retenção**: um escritório que já configurou a própria chave e tem processos monitorados tem
  custo de troca (switching cost) maior pra sair da plataforma.
- **Diferenciação de segurança**: ao contrário do LexCRM, aqui cada tenant usa sua própria chave e
  tem limite próprio — isso é vendável como argumento de confiança pra clientes maiores
  (isolamento real de dados e custo entre escritórios).

---

## Não decidido (fora do escopo deste ADR)

- **Resolvido:** modelo de billing é repasse — cada tenant compra o próprio crédito e cadastra a
  própria chave (confirmado pelo usuário, token de produção já criado e testado).
- Se `saldoLimiteReais` é hardcoded por enum de plano ou configurável por tenant individualmente
  (depende do modelo de billing que ainda não existe no sistema — fora do escopo deste ADR).
- Formato exato do payload de callback (`movimentacao_nova`, etc.) — ainda não observamos um evento
  real chegando no webhook.site de teste; o parser de `EscavadorCallbackController` deve ser escrito
  e ajustado contra o payload real assim que ele chegar, não só pela doc.
- UI de configuração (tela Angular) — este ADR cobre só backend/dados.
- Reconciliação de saldo: se o saldo real da conta Escavador (consultável via `GET
  /quantidade-creditos` na V2) deve ser sincronizado periodicamente com `EscavadorUso`, ou se
  `EscavadorUso` é só um limite interno independente do saldo real — evita surpresa se o tenant
  gastar crédito fora do nosso sistema (ex.: direto no painel do Escavador).

---

## Consequências

- Novo script `database/scripts/015_create_escavador.sql` com as 3 tabelas (`escavador_configuracao`,
  `escavador_uso`, `movimentacao`) + colunas novas em `processo` (`monitoramento_escavador_id`,
  `frequencia_monitoramento`), `tenant_id NOT NULL` + índice em todas, seguindo regra 7 do CLAUDE.md.
- Nova variável de ambiente `ESCAVADOR_ENCRYPTION_KEY` (chave AES-256, 32 bytes) — infraestrutura,
  não é o token do cliente.
- Endpoint de callback precisa estar documentado no nginx/CSP como rota pública sem autenticação de
  sessão, mas com rate limit próprio (o Escavador pode reenviar em caso de falha — tratar como
  esperado, não como ataque, mas ainda limitar volume por tenant).
- Teste de isolamento entre dois tenants obrigatório (regra 7 do CLAUDE.md): tenant A não pode ver
  configuração, uso ou movimentações do tenant B; um callback endereçado ao `tenantPublicId` de A
  nunca deve conseguir gravar dado em B mesmo com token de callback errado.
- Teste negativo de cota: chamada deve ser bloqueada ao atingir `saldoLimiteReais`.
- Teste negativo de callback: requisição sem o `Authorization` correto (ou com valor errado) deve
  ser rejeitada (401) sem processar o payload.
- Teste do `EscavadorClient`: garantir que nenhum método aceita URL de origem externa (usuário/
  frontend) como parâmetro de destino de chamada autenticada (mitigação estrutural da classe de bug
  SEC-007 do LexCRM).
- Teste de idempotência: enviar o mesmo payload de callback duas vezes não deve duplicar
  `Movimentacao` nem contabilizar custo duas vezes.

---

## Alternativas consideradas

| Alternativa | Motivo de rejeição |
|---|---|
| Token global (copiar o padrão do LexCRM) | Resolve rápido, mas reintroduz o problema estrutural de billing/isolamento que este ADR existe pra evitar |
| Polling próprio em vez de callback | Descartado após confirmar na doc oficial (e validar com chamada real) que a V1 oferece callback de verdade — polling seria reinventar algo que o Escavador já faz, gastando mais chamadas |
| Guardar payload bruto da API sem normalizar | Mais rápido de implementar, mas impede consulta/filtro de movimentações na UI sem reparsing a cada acesso |
| Cota em "número de chamadas" em vez de reais | Descartado depois de ver a tabela de preços real — custos variam de R$0,05 a R$4,50 por tipo de chamada; contar só quantidade mascararia o gasto real |
