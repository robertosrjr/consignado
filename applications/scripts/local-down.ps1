<#
.SYNOPSIS
  Encerra os processos iniciados por local-up.ps1 (ordem inversa: worker, ingestion, legado, local-env).
#>
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$pidFile = Join-Path (Join-Path $root '.local') 'pids.json'

if (-not (Test-Path $pidFile)) {
    Write-Host 'Nada a encerrar (.local\pids.json nao existe).'
    return
}

$pids = Get-Content $pidFile -Raw | ConvertFrom-Json
foreach ($name in 'worker', 'ingestion-api', 'mock-legacy', 'local-env') {
    $id = $pids.$name
    if ($null -ne $id -and (Get-Process -Id $id -ErrorAction SilentlyContinue)) {
        Stop-Process -Id $id -Force
        Write-Host "Encerrado $name (pid $id)"
    }
}
Remove-Item $pidFile -Force
