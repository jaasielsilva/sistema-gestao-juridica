# Teste de carga (k6)

Mede quantos usuários simultâneos o sistema suporta e como as listagens se comportam com muitos registros
(referência: 200.000 clientes e 200.000 processos no tenant de teste).

## Preparação (uma vez)
1. Instale o k6: `winget install GrafanaLabs.k6`.
2. Carga de dados no tenant de teste (opcional, mas é o que dá sentido ao teste):
   `database/seed/carga_clientes.sql` e `carga_processos.sql`.
3. Crie os usuários de teste (`carga01@carga.teste` … `carga50@carga.teste`, papel USER, mesmo tenant):
   - gere um hash BCrypt da senha de teste e troque `__HASH__` em `seed-usuarios.sql`;
   - rode o script com `sqlcmd -v tenant=2 qtd=50`.
   - Guarde a senha em `tests/load/.senha-teste` (está no `.gitignore`; nunca versione).

## Rodar
```powershell
$senha = (Get-Content tests\load\.senha-teste -Raw).Trim()
k6 run -e PERFIL=smoke -e "SENHA=$senha" tests\load\load.js
```

| `PERFIL` | O que faz | Duração |
|---|---|---|
| `smoke` | 2 usuários, valida o script | 1 min |
| `load` | rampa até 50 usuários e segura | ~13 min |
| `stress` | sobe 50 → 100 → 200 → 300 → 400 usuários | ~30 min |
| `spike` | salta de 10 para 200 usuários em 10 s | ~4 min |
| `soak` | 30 usuários por 90 min (vazamento de memória/conexões) | ~95 min |

Opções (`-e`): `BASE_URL` (padrão `https://erpcorporativo.cloud`), `USUARIOS` (quantos usuários de teste existem, padrão 50; com mais VUs que isso os usuários se repetem), `VUS` (simultâneos do perfil `load`, padrão 50), `THINK` (pausa média entre
ações em segundos; `0` = máxima vazão), `ESCRITA` (fração de iterações que gravam, padrão 0.08).
Use `--summary-export=arquivo.json` para guardar o resultado.

## O que cada usuário virtual faz
Login uma vez e renova o token a cada 10 min; depois repete: listar clientes (páginas iniciais, profundas e com filtros),
buscar por nome e por documento, abrir cliente e processo, listar processos por situação/tipo/cliente, resumos e,
em ~8% das iterações, criar + editar cliente e criar processo (marcados com `observacoes = 'CARGA_K6'`).

## Limites (thresholds)
leitura p95 < 500 ms e p99 < 1,5 s · busca p95 < 1,5 s · escrita p95 < 1 s · login p95 < 1,5 s · erros < 1 %.
O k6 sai com código 99 quando algum limite é ultrapassado (esperado no stress).

## Limpeza
- `limpar-dados-k6.sql` remove processos e clientes criados pelo teste (`CARGA_K6`).
- Usuários `carga*@carga.teste`: remova só depois de apagar as sessões deles.
- Os dados de `database/seed/` têm seus próprios scripts `carga_*_limpar.sql`.

## Cuidados
- **Rate limit do nginx (429):** o frontend limita 30 req/s por IP (burst 60). Como o k6 sai de um único IP, uma carga acima disso
  recebe 429, que o k6 conta como erro. Para medir só a aplicação, libere o IP do gerador (ou rode de vários IPs) durante o teste.
  Em produção o limite por IP também pode atingir escritórios inteiros atrás de um mesmo IP (NAT).
- Rode de uma máquina fora do servidor. Sem carga concorrente de outros sistemas na VPS, os números são fiéis;
  se houver outros apps na mesma VPS, a medição os inclui.
- Observe CPU/memória do banco e do backend durante o teste (Grafana ou `docker stats`).
- O limite de falhas de login (5 por e-mail, 30 por IP) só conta tentativas **erradas**; logins corretos não o acionam.
