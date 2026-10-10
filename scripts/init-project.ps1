<#
.SYNOPSIS
  Renomeia a casca para um produto novo (pacote Java, groupId, artifactId).
.EXAMPLE
  .\scripts\init-project.ps1 -Package br.com.minhaempresa.produto -ArtifactId produto
#>
param(
    [Parameter(Mandatory)][ValidatePattern('^[a-z][a-z0-9_]*(\.[a-z][a-z0-9_]*)+$')][string]$Package,
    [Parameter(Mandatory)][ValidatePattern('^[a-z][a-z0-9-]*$')][string]$ArtifactId
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$backend = Join-Path $root 'backend'
$oldPackage = 'com.example.app'
$utf8 = New-Object System.Text.UTF8Encoding($false)

if (-not (Test-Path (Join-Path $backend 'src\main\java\com\example\app'))) {
    throw "Pacote $oldPackage nao encontrado: a casca ja foi inicializada?"
}
if ($Package -eq $oldPackage) { throw 'Escolha um pacote diferente do padrao.' }

$groupId = $Package.Substring(0, $Package.LastIndexOf('.'))

# 1) Move os diretorios de main e test para o novo pacote
foreach ($area in 'main', 'test') {
    $base = Join-Path $backend "src\$area\java"
    $old = Join-Path $base 'com\example\app'
    if (-not (Test-Path $old)) { continue }
    $new = Join-Path $base ($Package -replace '\.', '\')
    New-Item -ItemType Directory -Force -Path (Split-Path $new) | Out-Null
    Move-Item $old $new
    # Remove diretorios vazios que sobraram (com\example)
    $leftover = Join-Path $base 'com\example'
    if ((Test-Path $leftover) -and -not (Get-ChildItem $leftover -Force)) { Remove-Item $leftover }
    $com = Join-Path $base 'com'
    if ((Test-Path $com) -and -not (Get-ChildItem $com -Force)) { Remove-Item $com }
}

# 2) Reescreve declaracoes de package/import nos .java
Get-ChildItem (Join-Path $backend 'src') -Recurse -Filter *.java | ForEach-Object {
    $text = [IO.File]::ReadAllText($_.FullName)
    $text = $text -replace [regex]::Escape($oldPackage), $Package
    [IO.File]::WriteAllText($_.FullName, $text, $utf8)
}

# 3) pom.xml: groupId, artifactId, name (somente do projeto, nao do parent)
$pomPath = Join-Path $backend 'pom.xml'
$pom = [IO.File]::ReadAllText($pomPath)
$pom = $pom -replace '(?s)(</parent>\s*<groupId>)[^<]+(</groupId>\s*<artifactId>)[^<]+(</artifactId>\s*<version>[^<]+</version>\s*<name>)[^<]+', "`${1}$groupId`${2}$ArtifactId`${3}$ArtifactId"
[IO.File]::WriteAllText($pomPath, $pom, $utf8)

Write-Host "Pronto. Pacote: $Package | groupId: $groupId | artifactId: $ArtifactId"
Write-Host 'Proximos passos: cd backend; mvn verify  |  preencha CLAUDE.md e docs/product/PRD.md'
