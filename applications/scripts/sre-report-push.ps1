[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$ReportDirectory,
    [Parameter(Mandatory = $true)][uri]$PushgatewayUrl,
    [Parameter(Mandatory = $true)][ValidatePattern('^[a-zA-Z0-9_.-]{1,64}$')][string]$Environment,
    [Parameter(Mandatory = $true)][ValidatePattern('^[a-zA-Z0-9_.-]{1,64}$')][string]$Instance
)
$ErrorActionPreference = 'Stop'
if (-not $PushgatewayUrl.IsAbsoluteUri -or $PushgatewayUrl.Scheme -notin @('http','https') -or
    $PushgatewayUrl.UserInfo -or $PushgatewayUrl.Query -or $PushgatewayUrl.Fragment -or
    $PushgatewayUrl.AbsolutePath -ne '/') {
    throw 'Informe somente a URL base HTTP(S) do Pushgateway, sem credenciais, caminho ou query.'
}
$metricsFile = Join-Path $ReportDirectory 'report.prom'
if (-not (Test-Path -LiteralPath $metricsFile -PathType Leaf)) {
    throw 'report.prom ausente. Gere novamente o relatorio com a versao atual da ferramenta.'
}
# PUT substitui o snapshot do grupo, removendo razoes antigas quando o novo resultado e incompleto.
# Nao usar ID de execucao no grupo: manter cardinalidade limitada por ambiente/origem do relatorio.
$target = $PushgatewayUrl.AbsoluteUri.TrimEnd('/') + '/metrics/job/sre-experiment/env/' +
    [uri]::EscapeDataString($Environment) + '/instance/' + [uri]::EscapeDataString($Instance)
Invoke-WebRequest -UseBasicParsing -Method Put -Uri $target -InFile $metricsFile `
    -ContentType 'text/plain; version=0.0.4; charset=utf-8' -TimeoutSec 15 | Out-Null
Write-Output 'Snapshot SRE enviado. A coleta do Prometheus e a visualizacao no Grafana devem ser verificadas.'
