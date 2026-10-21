# PRD — Sistema de Gestão Jurídica

> Rascunho inicial. A matriz abaixo adapta a referência visual fornecida e precisa ser validada pelo escritório antes de virar regra de autorização.

## 1. Problema
Que dor existe, para quem, e como e resolvida hoje?

## 2. Publico e usuarios
| Perfil | O que precisa fazer | Papel proposto no sistema |
|---|---|---|
| Administrador do sistema | Gerenciar usuários, perfis e configurações da plataforma | ADMINISTRADOR |
| Sócio (administrador do escritório) | Administrar clientes, processos, equipe e relatórios do escritório | SOCIO |
| Advogado | Acompanhar clientes, processos, documentos, prazos e tarefas | ADVOGADO |
| Assistente jurídico | Apoiar a gestão de processos e documentos com acesso limitado | ASSISTENTE_JURIDICO |
| Secretária | Atender clientes, organizar agenda, prazos e comunicações | SECRETARIA |
| Financeiro | Registrar movimentações e acompanhar pagamentos e relatórios financeiros | FINANCEIRO |

### 2.1 Matriz inicial de permissões — para validação

Legenda: **T** = acesso total à ação; **P** = acesso parcial, sujeito a regras de negócio; **—** = sem acesso. Esta matriz é uma proposta funcional, não uma autorização já implementada.

| Módulo / ação | Administrador do sistema | Sócio | Advogado | Assistente jurídico | Secretária | Financeiro |
|---|:---:|:---:|:---:|:---:|:---:|:---:|
| **Geral** |||||||
| Acessar o sistema | T | T | T | T | T | T |
| Gerenciar usuários e perfis | T | T | — | — | — | — |
| Configurar o sistema | T | P | — | — | — | — |
| **Clientes** |||||||
| Visualizar clientes | T | T | T | P | P | — |
| Cadastrar e editar clientes | T | T | T | — | P | — |
| Excluir clientes | T | P | — | — | — | — |
| **Processos** |||||||
| Visualizar processos | T | T | T | P | P | — |
| Cadastrar e editar processos | T | T | T | — | — | — |
| Excluir processos | T | T | P | — | — | — |
| Acessar documentos do processo | T | T | P | P | P | — |
| **Prazos e tarefas** |||||||
| Visualizar prazos | T | T | T | P | P | — |
| Gerenciar prazos | T | T | P | P | P | — |
| Visualizar tarefas | T | T | P | P | P | — |
| Gerenciar tarefas | T | T | P | — | — | — |
| **Financeiro** |||||||
| Visualizar informações financeiras | T | T | T | P | P | T |
| Cadastrar e editar lançamentos | T | — | — | — | — | T |
| Aprovar pagamentos | T | — | — | — | — | T |
| **Relatórios** |||||||
| Relatórios gerais | T | T | T | P | P | P |
| Relatórios financeiros | T | T | P | — | — | T |
| Relatórios de processos | T | T | — | — | — | T |
| **Comunicação** |||||||
| E-mail interno | T | T | T | P | P | P |
| Notificações e alertas | T | T | P | — | — | — |
| Chat e anotações | T | T | P | P | — | — |

Regras que devem ser detalhadas antes da implementação:
- “Parcial” precisa ser definido por ação e contexto (por exemplo, visualizar apenas processos atribuídos, editar sem excluir, ou acessar dados sem informações financeiras).
- Acesso a documentos deve respeitar o vínculo com o cliente/processo e eventuais restrições de sigilo.
- O administrador do sistema e o sócio são perfis distintos; validar se o primeiro é uma função interna da plataforma ou um usuário do escritório.
- Ações sensíveis (exclusão, aprovação de pagamentos e alteração de permissões) devem ter autorização no backend e trilha de auditoria.

## 3. Objetivo e metrica de sucesso
- Objetivo:
- Como saberemos que deu certo (numero, prazo):

## 4. Escopo do MVP
**Entra:**
-

**Fica de fora (de proposito):**
-

## 5. Fluxos principais
1.
2.

## 6. Regras de negocio
-

## 7. Dados principais
Entidades e relacionamentos (rascunho). Dados pessoais/sensiveis envolvidos (LGPD):

## 8. Requisitos nao funcionais
- Volume esperado:
- Disponibilidade:
- Auditoria/retencao:
- Multi-tenant? (sim/nao — decidir agora, ver ADR 0001)

## 9. Integracoes externas
-

## 10. Riscos e perguntas em aberto
- Confirmar se os seis perfis propostos correspondem aos perfis reais do escritório e se serão fixos ou configuráveis.
- Validar a matriz de permissões, especialmente o significado de acesso parcial e a separação entre administrador do sistema e sócio.
- Definir se advogados e assistentes só podem acessar processos/clientes atribuídos a eles e como funcionam substituições e equipes.
- Definir quem pode cadastrar/editar lançamentos, aprovar pagamentos e quais limites ou segregação de funções se aplicam.
