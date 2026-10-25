#!/usr/bin/env bash
# Deploy do Sistema de Gestão Jurídica na VPS. Chamado pelo GitHub Actions (.github/workflows/deploy.yml) por SSH,
# com uma chave restrita (`command=` no authorized_keys): o SHA do commit chega em SSH_ORIGINAL_COMMAND.
# Uso manual na VPS:  bash scripts/deploy-vps.sh <sha-de-40-caracteres-da-main>
#
# Fluxo: trava de concorrência -> sincroniza o código -> backup do banco -> scripts SQL -> build e subida -> verificação.
# Se a verificação falhar, volta o código anterior (os scripts SQL NÃO são desfeitos: veja o "Rollback:" de cada um).
set -euo pipefail

APP_DIR="${APP_DIR:-/opt/gestao-juridica}"
SHA="${1:-${SSH_ORIGINAL_COMMAND:-}}"
[[ "$SHA" =~ ^[0-9a-f]{40}$ ]] || { echo "SHA invalido: informe os 40 caracteres do commit." >&2; exit 2; }

cd "$APP_DIR"
COMPOSE=(docker compose -f docker-compose.vps.yml -p gestaojuridica)

exec 9>/var/lock/gestaojuridica-deploy.lock
flock -n 9 || { echo "Ja existe um deploy em andamento." >&2; exit 3; }

# Etapa 1: atualiza o código e reexecuta a versão NOVA deste script (assim o próprio deploy também é versionado).
if [ -z "${DEPLOY_REEXEC:-}" ]; then
  git fetch --prune origin main
  git cat-file -e "$SHA^{commit}" 2>/dev/null || { echo "Commit $SHA nao encontrado em origin." >&2; exit 2; }
  git merge-base --is-ancestor "$SHA" origin/main || { echo "Commit $SHA nao pertence a main." >&2; exit 2; }
  PREV_SHA="$(git rev-parse HEAD 2>/dev/null || true)"
  git reset --hard "$SHA"
  DEPLOY_REEXEC=1 PREV_SHA="$PREV_SHA" exec bash scripts/deploy-vps.sh "$SHA"
fi

PREV_SHA="${PREV_SHA:-}"
echo "== Deploy $SHA (anterior: ${PREV_SHA:-nenhum})"

set -a; . ./.env; set +a
DB_NAME="${DB_NAME:-appdb}"

sqlcmd_db() {
  docker exec -i -e P="$DB_PASSWORD" gestaojuridica-db-1 bash -c \
    "/opt/mssql-tools18/bin/sqlcmd -C -b -S localhost -U sa -P \"\$P\" $*"
}

# Backup antes de qualquer script SQL novo (fica no volume do banco; mantém os 5 mais recentes).
if docker inspect -f '{{.State.Running}}' gestaojuridica-db-1 2>/dev/null | grep -q true; then
  echo "== Backup do banco"
  docker exec gestaojuridica-db-1 mkdir -p /var/opt/mssql/backups
  sqlcmd_db "-Q \"BACKUP DATABASE [$DB_NAME] TO DISK=N'/var/opt/mssql/backups/pre-deploy-${SHA:0:12}.bak' WITH COMPRESSION, INIT\"" >/dev/null
  docker exec gestaojuridica-db-1 bash -c 'ls -1t /var/opt/mssql/backups/pre-deploy-*.bak | tail -n +6 | xargs -r rm -f'
fi

verificar() {
  for _ in $(seq 1 40); do
    if curl -fsS -o /dev/null http://127.0.0.1:3010/ && \
       curl -fsS http://127.0.0.1:3010/api/v1/auth/config | grep -q registrationEnabled; then
      return 0
    fi
    sleep 3
  done
  return 1
}

subir() {
  "${COMPOSE[@]}" run --rm db-scripts
  "${COMPOSE[@]}" up -d --build backend frontend
}

echo "== Scripts SQL, build e subida"
if subir && verificar; then
  echo "== Deploy OK: $SHA"
  exit 0
fi

echo "== FALHA na verificacao do deploy $SHA" >&2
"${COMPOSE[@]}" logs --tail 40 backend >&2 || true
if [ -n "$PREV_SHA" ] && [ "$PREV_SHA" != "$SHA" ]; then
  echo "== Voltando o codigo para $PREV_SHA (scripts SQL ja aplicados nao sao desfeitos)" >&2
  git reset --hard "$PREV_SHA"
  "${COMPOSE[@]}" up -d --build backend frontend && verificar && echo "== Rollback OK" >&2 || echo "== Rollback tambem falhou: intervenha manualmente." >&2
fi
exit 1
