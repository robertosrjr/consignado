<#
.SYNOPSIS
  Controla uma falha temporaria no mock local, sem reiniciar processos ou limpar estado.
.DESCRIPTION
  Exige local-up.ps1 -SreFaultControl e o token em LEGACY_FAULT_CONTROL_TOKEN.
  PrepareOnly grava somente o manifest, sem rede ou segredo. O manifest nao comprova
  preflight completo, resultado de negocio, entrega de alerta ou SLI.
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidateSet('Status', 'Activate', 'Restore')]
    [string]$Action,
    [ValidateRange(1, 65535)] [int]$Port = 8081,
    [ValidateRange(0, 10000)] [long]$LatencyMs = 0,
    [ValidateRange(0.0, 1.0)] [double]$ErrorRate = 1.0,
    [ValidateRange(1, 300000)] [long]$DurationMs = 2000,
    [switch]$PrepareOnly
)
$ErrorActionPreference = 'Stop'
if ([double]::IsNaN($ErrorRate) -or [double]::IsInfinity($ErrorRate) -or
    ($Action -eq 'Activate' -and $LatencyMs -eq 0 -and $ErrorRate -eq 0)) {
    throw 'A ativacao exige latencia ou taxa de erro finita e nao nula.'
}
$uri = "http://127.0.0.1:$Port/internal/sre/fault"
$directory = Join-Path (Split-Path -Parent $PSScriptRoot) '.local/sre/fault-control'
[void][IO.Directory]::CreateDirectory($directory)
$manifestPath = Join-Path $directory (([guid]::NewGuid().ToString()) + '.json')
$manifest = [ordered]@{
    schemaVersion = 1
    startedAtUtc = [DateTime]::UtcNow.ToString('o')
    action = $Action
    endpoint = $uri
    status = 'PREPARED'
    requested = $null
    observedBefore = $null
    observedAfter = $null
    finishedAtUtc = $null
    scope = 'control-only; not a business recovery or SLI report'
}
if ($Action -eq 'Activate') {
    $manifest.requested = [ordered]@{ latencyMs = $LatencyMs; errorRate = $ErrorRate; durationMs = $DurationMs }
}
try {
    if (-not $PrepareOnly) {
        if ([string]::IsNullOrWhiteSpace($env:LEGACY_FAULT_CONTROL_TOKEN) -or $env:LEGACY_FAULT_CONTROL_TOKEN.Length -lt 32) {
            throw 'Token de controle ausente ou curto.'
        }
        $headers = @{ 'X-SRE-Control-Token' = $env:LEGACY_FAULT_CONTROL_TOKEN }
        $before = Invoke-RestMethod -Uri $uri -Method Get -Headers $headers -TimeoutSec 5
        $manifest.observedBefore = $before
        switch ($Action) {
            'Status' { $manifest.observedAfter = $before }
            'Restore' {
                $manifest.observedAfter = Invoke-RestMethod -Uri $uri -Method Delete -Headers $headers -TimeoutSec 5
                if ($manifest.observedAfter.active) { throw 'Restauracao nao confirmada.' }
            }
            'Activate' {
                if ($before.active) { throw 'Ja existe uma falha ativa; aguarde expiracao ou restaure.' }
                $body = $manifest.requested | ConvertTo-Json -Compress
                $manifest.observedAfter = Invoke-RestMethod -Uri $uri -Method Post -Headers $headers -ContentType 'application/json' -Body $body -TimeoutSec 5
                if (-not $manifest.observedAfter.active) { throw 'Ativacao nao observada; confira estado e prazo.' }
            }
        }
        $manifest.status = 'OBSERVED'
    }
} catch {
    $manifest.status = 'INCOMPLETE'
    # Nao imprimir resposta/headers: podem conter material sensivel do ambiente.
    throw 'Controle nao concluido. Consulte o manifest sanitizado e confirme Status/Restore; eventual falha expira no mock pelo TTL.'
} finally {
    $manifest.finishedAtUtc = [DateTime]::UtcNow.ToString('o')
    [IO.File]::WriteAllText($manifestPath, ($manifest | ConvertTo-Json -Depth 5), [Text.UTF8Encoding]::new($false))
    Write-Output "Manifest: $manifestPath"
}
Write-Output ($manifest | ConvertTo-Json -Depth 5)
