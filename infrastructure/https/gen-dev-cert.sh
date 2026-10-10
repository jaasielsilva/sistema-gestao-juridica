#!/usr/bin/env bash
# Gera um certificado AUTOASSINADO apenas para testar HTTPS localmente. Nao serve para producao.
# Uso: bash infrastructure/https/gen-dev-cert.sh
set -euo pipefail
dir="$(cd "$(dirname "$0")" && pwd)/certs"
mkdir -p "$dir"
cd "$dir" # caminhos relativos: o openssl do Git for Windows nao entende /c/...
MSYS_NO_PATHCONV=1 openssl req -x509 -newkey rsa:2048 -nodes -days 365 \
  -keyout privkey.pem -out fullchain.pem \
  -subj "/CN=localhost" -addext "subjectAltName=DNS:localhost,IP:127.0.0.1" 2>/dev/null
echo "Certificado de desenvolvimento gerado em $dir"
