<#
.SYNOPSIS
  Configura a esteira de deploy da HOMOLOGACAO: chave SSH propria + secrets do ambiente "homologacao" no GitHub.

.DESCRIPTION
  Rode UMA vez, no seu computador, com o seu acesso SSH de administrador a VPS e o `gh` autenticado.
  O que faz (nada e gravado sem a sua confirmacao):
    1. Gera um par de chaves ed25519 so para a homologacao (numa pasta temporaria).
    2. Na VPS, em ~/.ssh/authorized_keys: faz backup, troca a linha da chave de homologacao e instala a nova,
       RESTRITA a scripts/deploy-vps.sh com APP_DIR/COMPOSE_PROJECT/FRONTEND_PORT de homologacao.
    3. Grava no GitHub (environment "homologacao") os secrets SSH_PRIVATE_KEY_HOMOLOG, SSH_HOST, SSH_USER,
       SSH_KNOWN_HOSTS. A chave privada nunca e exibida.
    4. Apaga as chaves da pasta temporaria.

  A producao NAO e tocada: o environment "producao" e as linhas de producao do authorized_keys ficam como estao.

.PARAMETER VpsHost
  Endereco da VPS (o mesmo do secret SSH_HOST de producao).

.PARAMETER VpsUser
  Usuario de deploy na VPS (o mesmo do secret SSH_USER de producao; e o dono do authorized_keys usado pela esteira).

.EXAMPLE
  .\scripts\configurar-homologacao.ps1 -VpsHost 187.77.245.121 -VpsUser deploy
#>
[CmdletBinding()]
param(
  [Parameter(Mandatory)] [string] $VpsHost,
  [Parameter(Mandatory)] [string] $VpsUser,
  [string] $Repo = 'jaasielsilva/sistema-gestao-juridica',
  [string] $AppDir = '/opt/gestao-juridica-homolog',
  [string] $ComposeProject = 'gestao-juridica-homolog',
  [int]    $FrontendPort = 3011
)

$ErrorActionPreference = 'Stop'
$comentario = 'gha-gestaojuridica-homolog'   # identifica a chave de homologacao no authorized_keys

foreach ($cmd in 'ssh', 'ssh-keygen', 'ssh-keyscan', 'gh') {
  if (-not (Get-Command $cmd -ErrorAction SilentlyContinue)) { throw "Comando '$cmd' nao encontrado no PATH." }
}
gh auth status *> $null
if ($LASTEXITCODE -ne 0) { throw "O gh nao esta autenticado. Rode: gh auth login" }

$pasta = Join-Path ([IO.Path]::GetTempPath()) ("homolog-key-" + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $pasta | Out-Null
$chave = Join-Path $pasta 'id_ed25519'

try {
  # 1) Chave nova, sem senha (a esteira nao tem como digita-la). O arquivo fica so nesta pasta temporaria.
  ssh-keygen -q -t ed25519 -f $chave -N '""' -C $comentario
  if ($LASTEXITCODE -ne 0) { throw 'Falha ao gerar a chave.' }
  $publica = (Get-Content -Raw "$chave.pub").Trim()

  $comando = "APP_DIR=$AppDir COMPOSE_PROJECT=$ComposeProject FRONTEND_PORT=$FrontendPort bash $AppDir/scripts/deploy-vps.sh"
  $linha = "command=`"$comando`",restrict $publica"

  Write-Host ""
  Write-Host "Vou fazer o seguinte:" -ForegroundColor Cyan
  Write-Host "  VPS  : $VpsUser@$VpsHost  ->  ~/.ssh/authorized_keys (backup antes; troca so a linha '$comentario')"
  Write-Host "  Linha: command=`"$comando`",restrict ssh-ed25519 <chave publica> $comentario"
  Write-Host "  GitHub ($Repo), environment 'homologacao': SSH_PRIVATE_KEY_HOMOLOG, SSH_HOST, SSH_USER, SSH_KNOWN_HOSTS"
  Write-Host ""
  $resp = Read-Host "Confirma? (digite SIM)"
  if ($resp -ne 'SIM') { Write-Host 'Cancelado. Nada foi alterado.'; return }

  # 2) VPS: backup + remove a linha antiga da chave de homologacao + instala a nova. Script enviado por stdin.
  $remoto = @"
set -euo pipefail
f="`$HOME/.ssh/authorized_keys"
mkdir -p "`$HOME/.ssh" && chmod 700 "`$HOME/.ssh"
touch "`$f" && chmod 600 "`$f"
cp -a "`$f" "`$f.bak-`$(date +%Y%m%d-%H%M%S)"
grep -v -F ' $comentario' "`$f" > "`$f.novo" || true
printf '%s\n' '$linha' >> "`$f.novo"
mv "`$f.novo" "`$f" && chmod 600 "`$f"
echo "authorized_keys atualizado: `$(grep -c -F ' $comentario' "`$f") linha(s) de homologacao."
"@
  ($remoto -replace "`r", '') | ssh -o StrictHostKeyChecking=yes "$VpsUser@$VpsHost" 'bash -s'
  if ($LASTEXITCODE -ne 0) { throw 'Falha ao atualizar o authorized_keys na VPS (nada foi gravado no GitHub).' }

  # 3) GitHub: secrets do environment homologacao.
  Get-Content -Raw $chave | gh secret set SSH_PRIVATE_KEY_HOMOLOG --env homologacao --repo $Repo
  if ($LASTEXITCODE -ne 0) { throw 'Falha ao gravar SSH_PRIVATE_KEY_HOMOLOG.' }
  gh secret set SSH_HOST --env homologacao --repo $Repo --body $VpsHost
  gh secret set SSH_USER --env homologacao --repo $Repo --body $VpsUser
  (ssh-keyscan -H $VpsHost 2>$null) -join "`n" | gh secret set SSH_KNOWN_HOSTS --env homologacao --repo $Repo
  if ($LASTEXITCODE -ne 0) { throw 'Falha ao gravar SSH_KNOWN_HOSTS.' }

  # 4) Conferencia: so os NOMES (o GitHub nunca devolve os valores).
  Write-Host ""
  Write-Host "Secrets do environment 'homologacao':" -ForegroundColor Green
  gh secret list --env homologacao --repo $Repo
  Write-Host ""
  Write-Host "Pronto. Agora dispare o deploy: Actions > Deploy Homologacao > Run workflow (ou faca merge na dev)." -ForegroundColor Green
}
finally {
  # A chave privada so existe nesta pasta; apaga sempre, mesmo se algo falhar.
  Remove-Item -Recurse -Force $pasta -ErrorAction SilentlyContinue
}
