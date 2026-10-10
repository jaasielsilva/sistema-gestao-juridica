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

# Usuario de runtime da aplicacao (permissoes minimas). A senha vem do ambiente e e validada
# contra um conjunto seguro de caracteres, pois e interpolada pelo sqlcmd (-v).
: "${APP_DB_USER:?APP_DB_USER e obrigatorio (usuario de runtime, ex.: app_runtime)}"
: "${APP_DB_PASSWORD:?APP_DB_PASSWORD e obrigatorio}"
[[ "$APP_DB_USER" =~ ^[A-Za-z_][A-Za-z0-9_]{2,63}$ ]] || { echo "APP_DB_USER invalido" >&2; exit 1; }
[[ "$APP_DB_PASSWORD" =~ ^[A-Za-z0-9@#%^*_+=.!-]{12,128}$ ]] || {
  echo "APP_DB_PASSWORD invalida: use 12+ caracteres entre letras, numeros e @#%^*_+=.!-" >&2; exit 1; }
# Politica do SQL Server: 3 das 4 classes (maiuscula, minuscula, numero, simbolo).
classes=0
[[ "$APP_DB_PASSWORD" =~ [A-Z] ]] && classes=$((classes + 1))
[[ "$APP_DB_PASSWORD" =~ [a-z] ]] && classes=$((classes + 1))
[[ "$APP_DB_PASSWORD" =~ [0-9] ]] && classes=$((classes + 1))
[[ "$APP_DB_PASSWORD" =~ [@#%^*_+=.!-] ]] && classes=$((classes + 1))
[ "$classes" -ge 3 ] || {
  echo "APP_DB_PASSWORD fraca: precisa misturar 3 de 4 tipos (maiuscula, minuscula, numero, simbolo @#%^*_+=.!-)" >&2; exit 1; }
[ "$APP_DB_USER" != "$DB_USER" ] || { echo "APP_DB_USER nao pode ser o mesmo usuario administrativo" >&2; exit 1; }

echo "* aplicando permissoes do usuario $APP_DB_USER"
run -d "$DB_NAME" -v APP_DB_USER="$APP_DB_USER" APP_DB_PASSWORD="$APP_DB_PASSWORD" -i "$(dirname "$0")/post/grants.sql"
echo "Banco em dia."
