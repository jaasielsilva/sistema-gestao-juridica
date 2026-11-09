# Manual de uso — Integração com o Escavador

Guia para o escritório cliente usar o monitoramento automático de processos. Escrito para quem
usa o sistema no dia a dia (advogado, admin do escritório), não para quem desenvolve.

---

## Linha do tempo — da perspectiva do escritório cliente

### Dia 0 — Configuração inicial (uma vez só, por escritório)

1. Crie uma conta na [API Business do Escavador](https://api.escavador.com) e compre o crédito
   (mínimo R$200, válido por 3 meses — "usa ou perde").
2. No painel do Escavador, gere um **token de acesso** (Personal Access Token). Copie — ele só
   aparece uma vez.
3. Ainda no painel do Escavador, na seção de **Callbacks**, gere um **segredo de callback**. Copie também.
4. No nosso sistema, vá em **Integrações → Escavador** (menu lateral, visível só para admin).
5. Cole o token e o segredo de callback nos campos correspondentes. Defina o **limite mensal de
   gasto** (em reais) — o sistema nunca deixa o monitoramento passar desse valor.
6. Clique em **Salvar**. A tela vai mostrar uma **URL de callback** — copie essa URL.
7. Volte ao painel do Escavador, cole essa URL na configuração de Callbacks da conta. A partir
   daqui, o Escavador sabe para onde avisar quando algo mudar num processo monitorado.

> Isso é feito uma única vez por escritório. Depois disso, ninguém mais precisa tocar nessa tela,
> a menos que o token expire ou o limite de gasto precise mudar.

### Dia 1 — Cadastro do cliente e do processo (fluxo que já existia)

8. Cadastre o cliente normalmente em **Clientes → Novo cliente**.
9. Cadastre o processo normalmente em **Processos → Novo processo**, com o número CNJ correto.

### Dia 1 — Ativação do monitoramento (ponto novo)

10. Abra a ficha do processo recém-criado (clique no processo na listagem).
11. Na seção **Monitoramento Escavador**, escolha a frequência:
    - **Mensal** (R$ 0,08/mês) — processos parados, sem movimento esperado no curto prazo.
    - **Semanal** (R$ 0,32/mês) — acompanhamento de rotina.
    - **Diária** (R$ 1,76/mês) — processos com audiência ou decisão iminente.
12. Clique em **Ativar monitoramento**.

> Se a ativação falhar com a mensagem "ultrapassa o limite mensal configurado", significa que a
> soma dos processos já monitorados mais esse novo já passa do limite definido no passo 5. Ou
> aumenta o limite, ou reduz a frequência de algum processo (ou desativa um que não precisa mais).

### Dia 2 em diante — Automático, sem ação do advogado

13. O robô do Escavador roda sozinho, na frequência escolhida. Se não houve nada novo, nada
    acontece na tela — é o esperado, não é erro.
14. Quando o processo tiver uma movimentação nova, ela aparece automaticamente na ficha do
    processo, na mesma seção de monitoramento, com data e descrição.

### Fim do mês — Controle de gasto

15. Volte em **Integrações → Escavador** para ver o gasto mensal estimado com monitoramento,
    comparado ao limite configurado. Ajuste frequências ou o limite se necessário.

---

## Perguntas frequentes

**Ativei o monitoramento e não apareceu nada ainda. Está quebrado?**
Não necessariamente. O robô só avisa quando *encontra algo novo*. Se o processo está parado, o
silêncio é o resultado esperado. A frequência escolhida (mensal/semanal/diária) é quando o robô
*verifica*, não uma garantia de que vai achar novidade.

**Posso desativar o monitoramento de um processo?**
Sim, na mesma seção da ficha do processo, com o botão "Desativar monitoramento". Isso remove o
custo mensal daquele processo imediatamente.

**O que acontece se o crédito do Escavador acabar?**
As chamadas futuras falham (o Escavador recusa por falta de saldo). O sistema mostra o erro "Sem
saldo na conta Escavador do escritório". É preciso recarregar o crédito direto no painel do
Escavador — o nosso sistema não gerencia a recarga.

**Cada escritório paga sua própria conta do Escavador?**
Sim. Cada escritório que usa essa integração compra o próprio crédito e cadastra a própria chave.
Nenhum escritório compartilha saldo ou dados de monitoramento com outro.

**O que é o "limite mensal de gasto" que configuro no nosso sistema?**
É um limite interno, que o nosso sistema aplica *antes* de ativar qualquer novo monitoramento —
para impedir que a soma dos processos monitorados ultrapasse o que o escritório decidiu gastar por
mês. Ele não substitui o saldo real da conta Escavador, que é controlado lá no painel deles.

---

## Para o administrador do sistema (operação)

- Trocar a chave ou o segredo de callback: repetir os passos 2–7 na tela de configuração — isso
  sobrescreve as credenciais anteriores.
- Rotação de chave comprometida: gerar um novo token/segredo no painel do Escavador e repetir a
  configuração; não é necessário reativar monitoramentos já existentes.
- Ver detalhes técnicos da arquitetura, decisões de segurança e tabela de preços: [ADR 0007](adr/0007-integracao-escavador.md).
