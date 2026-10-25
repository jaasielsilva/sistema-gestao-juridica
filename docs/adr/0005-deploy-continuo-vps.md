# ADR 0005 — Deploy contínuo na VPS (GitHub Actions)

## Contexto
O sistema roda em uma VPS própria (Docker Compose + nginx do host), como os outros produtos do escritório. O deploy
era manual. Queremos o mesmo fluxo dos demais projetos: PR → CI → merge na `main` → produção, sem acesso manual ao servidor.

## Decisão
- **`main` protegida:** só recebe alterações por pull request, com os jobs do CI aprovados
  (`backend`, `frontend`, `database-scripts`, `docker-build`).
- **`.github/workflows/deploy.yml`** dispara quando o workflow `CI` termina com sucesso em um *push* na `main`
  (execuções semanais e PRs não publicam). Também aceita disparo manual com um SHA da `main`.
  Roda no *environment* `producao`.
- **Acesso por SSH com chave restrita:** a chave do Actions só executa `scripts/deploy-vps.sh`
  (`command=` e `restrict` no `authorized_keys`) e recebe apenas o SHA do commit. O script recusa SHA que não esteja na `main`.
  O host é validado por `SSH_KNOWN_HOSTS` (sem confiar no primeiro contato).
- **`scripts/deploy-vps.sh`** (na VPS, versionado no repositório): trava de concorrência → `git reset --hard <sha>` →
  backup do banco → `db-scripts` (scripts SQL numerados, regra do projeto) → `docker compose up -d --build` →
  verificação de saúde → rollback do código se falhar.
- **Segredos** ficam no GitHub (`SSH_HOST`, `SSH_USER`, `SSH_PRIVATE_KEY`, `SSH_KNOWN_HOSTS` no environment `producao`)
  e no `.env`/`.env.smtp` da VPS (fora do Git). Nada de segredo no repositório.

## Consequências
- Todo código em produção passou por PR e CI. Rastreabilidade: cada deploy tem o SHA e o log do workflow.
- Rollback automático cobre só o código. Scripts SQL já aplicados **não** são desfeitos; cada um tem `-- Rollback:` no cabeçalho
  e o backup `pre-deploy-<sha>.bak` fica em `/var/opt/mssql/backups` (volume do banco, mesmo disco: não substitui backup externo).
- O build roda na VPS (como nos outros projetos): simples, mas disputa CPU com o que já roda lá durante o deploy.
- Migrações destrutivas exigem cuidado em PR: o deploy as aplica automaticamente.
