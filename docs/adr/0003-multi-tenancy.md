# ADR 0003 — Multi-tenancy por coluna discriminadora

- **Status:** aceito
- **Data:** 2026-10-10

## Contexto
Produtos SaaS atendem várias empresas no mesmo sistema. Uma empresa nunca pode ver, alterar ou apagar dados de outra. Isso precisa existir desde o começo: adicionar depois significa reescrever todas as consultas.

## Decisão
- Cada usuário pertence a **um tenant** (`usuario.tenant_id`). O tenant vai assinado no access token (`tid`) e é colocado no `TenantContext` **somente** pelo filtro JWT. Nunca vem de parâmetro, header ou corpo.
- Toda tabela de negócio tem `tenant_id NOT NULL` (FK + índice com `tenant_id` na frente).
- Toda entidade de negócio estende `TenantScopedEntity` (`@TenantId` do Hibernate): consultas JPQL/derivadas são filtradas por tenant **automaticamente** e o `tenant_id` é preenchido no INSERT.
- Todo repositório estende `TenantScopedRepository`, que redeclara `findById` como consulta JPQL.
- Rede de segurança: `@PostLoad` na entidade aborta o carregamento se a linha for de outro tenant (resposta 404 + log de erro).
- Sem contexto de tenant (sem login) o identificador é `-1`: nenhuma linha existe com esse id, então as consultas **falham fechadas** (voltam vazias).
- Cadastro pede o nome da organização e cria um tenant com ID obtido de uma sequence do banco. A primeira conta recebe papel `ADMIN` apenas dentro desse tenant; papel e tenant nunca são aceitos do cliente.

## Um achado importante (testado)
O Hibernate **não aplica o filtro de tenant em `EntityManager.find`** — e, portanto, no `findById` padrão do Spring Data. O teste de isolamento (`tenantNaoEnxergaDadosDeOutroTenantPorListagemIdOuExclusao`) pegou exatamente isso: o tenant B conseguiu ler a nota do tenant A pelo ID. Por isso existem as duas camadas acima. **Não** use `find`, `getReferenceById` ou SQL nativo nas entidades de tenant sem filtrar manualmente.

## Limites
- É isolamento **lógico** (mesmo banco, mesmas tabelas). Não protege contra um administrador do banco, nem contra consultas SQL nativas que esqueçam `tenant_id`. Requisitos regulatórios fortes podem exigir banco/schema por cliente.
- A tabela `usuario` e as de segurança (sessões, tokens, auditoria) são globais; a auditoria é filtrada por tenant na consulta administrativa (`AdminAuditController`).
- Tarefas assíncronas e jobs não têm tenant no contexto: devem receber o `tenant_id` explicitamente e defini-lo no `TenantContext` antes de acessar dados de negócio.
- `e-mail` é único globalmente (um e-mail = uma conta = um tenant). O endpoint de cadastro responde de forma genérica para não revelar se o endereço já existe.
- A migration `009_tenant_isolation_hardening.sql` separa usuários legados que estavam no tenant 1 em workspaces individuais, move suas notas e eventos de auditoria pelo usuário e revoga as sessões existentes. Como o fluxo antigo não armazenava associação organizacional, essa separação conservadora evita manter contas distintas compartilhando dados.
