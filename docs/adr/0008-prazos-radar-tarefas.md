# ADR 0008 — Prazos, radar de processos e tarefas (Kanban)

- **Status:** aceito (2026-11-20)
- **Contexto:** o sistema só guardava um `proximo_prazo` em texto no processo, não dizia há quanto tempo um processo
  está parado e não tinha tarefas. Referência de mercado: o JurisCRM do escritório (prazos com catálogo de tipos,
  radar de processos parados, tarefas em listas).

## Decisões

**Prazos (`prazo/`, script 017)**
- Vários prazos por processo, com tipo de um **catálogo** (enum `TipoPrazo`, ~40 tipos em 9 categorias), vencimento,
  responsável opcional e baixa (`ABERTO`/`CUMPRIDO`). O catálogo vai ao frontend por `GET /prazos/tipos`, então
  acrescentar um tipo não exige mudar a tela. `OUTRO` exige descrição.
- A situação **HOJE / PRÓXIMO / VENCIDO não é gravada**: é calculada a cada leitura a partir de status + data, para
  nunca ficar velha de um dia para o outro. "Hoje" é a data em `America/Sao_Paulo` (bean `Clock`), não a do servidor (UTC).
- `processo.proximo_prazo` continua existindo (a listagem de processos usa) e passa a ser **recalculado pelo serviço**
  a cada mudança de prazo (menor vencimento aberto; vazio se não houver). Alternativa descartada: calcular por join na
  listagem, que custaria uma subconsulta por linha com 200 mil processos.
- Excluir prazo é só `ADMIN`; baixar e reabrir, qualquer usuário.

**Radar (`processo/Radar*`, script 018)**
- Processo ganha `etapa` (8 etapas fixas, enum `EtapaProcesso`) e `etapa_atualizada_em`. **Dias na etapa** = dias inteiros
  desde a entrada na etapa atual. Faixas: OK até 3, ATENÇÃO de 4 a 7, TRAVADO a partir de 8.
- Entram no radar processos `EM_ANDAMENTO`, `PENDENTE` e `URGENTE`; `ENCERRADO` e `SUSPENSO` ficam de fora.
- Só mudar de etapa reinicia a contagem (`PUT /processos/{id}/etapa`); repetir a etapa atual não reinicia. Movimentação
  do Escavador e novos prazos **não** reiniciam: ficam como evolução (o "parado" é sobre o fluxo do escritório).
- Etapas fixas por enquanto; etapas configuráveis por escritório exigiriam tabela própria e ficam para quando houver demanda.
- Índice `ix_processo_tenant_radar (tenant_id, etapa_atualizada_em, id) INCLUDE (situacao)`: o mais parado vem primeiro
  sem ordenar. O script faz backfill de `etapa_atualizada_em` com `criado_em` (processos antigos aparecem como parados).

**Tarefas (`tarefa/`, script 019)**
- Quadro Kanban com 4 colunas fixas (`A_FAZER`, `EM_ANDAMENTO`, `AGUARDANDO`, `CONCLUIDA`), ligadas a um processo e a um
  responsável (ambos opcionais). `ordem` é a posição na coluna; mover renumera a coluna de destino (0..n-1). Concluídas
  não renumeram (a coluna cresce) e só aparecem no quadro por 30 dias; o quadro devolve no máximo 500 cartões.
- Apagar: quem criou ou `ADMIN`.

**Transversal**
- Responsável é sempre um usuário **ativo do mesmo tenant**, validado no servidor (`ResponsavelService`). `Usuario` não é
  `@TenantId` (o login o busca antes de conhecer o tenant), então o tenant é passado explicitamente na consulta.
  `GET /usuarios/responsaveis` devolve só id e nome (nunca e-mail, papel ou dado de segurança).
- Todas as entidades novas estendem `TenantScopedEntity` e os repositórios `TenantScopedRepository`.

## Consequências
- Três scripts novos (017–019) e três módulos com teste de isolamento entre dois tenants.
- Não há ainda alerta ativo (e-mail/sino) de prazo vencendo: a tela mostra, mas ninguém é avisado. Próximo passo natural.
- Radar e prazos não consultam o Escavador; ligar movimentação → sugestão de prazo é evolução futura.
