#!/usr/bin/env bash
# Aplica os scripts de database/scripts em ordem, uma unica vez cada, validando checksum.
# Uso (dentro do container de migracao): ./apply.sh
# Variaveis: DB_HOST, DB_NAME, DB_USER, DB_PASSWORD
#
# Regras:
#  - Script ja aplicado com o MESMO checksum  -> ignorado.
#  - Script ja aplicado com checksum DIFERENTE -> ERRO (alguem editou um script publicado; crie um novo).
#  - Script novo -> executado com -b (aborta no primeiro erro) e registrado.
set -euo pipefail

DB_HOST="${DB_HOST:-db}"
DB_NAME="${DB_NAME:-appdb}"
DB_USER="${DB_USER:-sa}"
: "${DB_PASSWORD:?DB_PASSWORD e obrigatorio}"

SCRIPTS_DIR="$(cd "$(dirname "$0")/scripts" && pwd)"
SQLCMD="/opt/mssql-tools18/bin/sqlcmd"
run()   { "$SQLCMD" -C -b -S "$DB_HOST" -U "$DB_USER" -P "$DB_PASSWORD" "$@"; }
query() { run -d "$DB_NAME" -h -1 -W -Q "SET NOCOUNT ON; $1" | tr -d '\r' | head -n1; }

echo "Aguardando SQL Server em $DB_HOST..."
for _ in $(seq 1 60); do run -Q "SELECT 1" >/dev/null 2>&1 && break; sleep 2; done
run -Q "SELECT 1" >/dev/null

run -Q "IF DB_ID(N'$DB_NAME') IS NULL CREATE DATABASE [$DB_NAME];"

for file in $(ls "$SCRIPTS_DIR"/*.sql | sort); do
  name="$(basename "$file")"
  sum="$(sha256sum "$file" | cut -d' ' -f1)"

  # 000 cria a propria tabela de controle; e idempotente, entao sempre roda.
  if [ "$name" = "000_schema_control.sql" ]; then
    run -d "$DB_NAME" -i "$file"
    query "IF NOT EXISTS (SELECT 1 FROM dbo.schema_version WHERE script='$name') INSERT INTO dbo.schema_version(script,checksum) VALUES('$name','$sum');" >/dev/null || true
    continue
  fi

  applied="$(query "SELECT checksum FROM dbo.schema_version WHERE script='$name'" || true)"
  if [ -n "$applied" ]; then
    if [ "$applied" != "$sum" ]; then
      echo "ERRO: $name foi alterado depois de aplicado (checksum diverge). Nao edite scripts publicados; crie um novo." >&2
      exit 1
    fi
    echo "= $name (ja aplicado)"
    continue
  fi

  echo "+ aplicando $name"
  run -d "$DB_NAME" -i "$file"
  query "INSERT INTO dbo.schema_version(script,checksum) VALUES('$name','$sum');" >/dev/null
done
echo "Banco em dia."
