[CmdletBinding()]
param(
    [ValidateRange(5,60)][int]$Seconds = 15,
    [string]$Rates = '1,3,5,10,30,60,120,240',
    [switch]$TestWorkerRestart
)
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$state = Join-Path $root '.local'
if (Test-Path (Join-Path $state 'pids.json')) { throw 'Existe sessao local registrada; encerre-a antes deste ensaio.' }
if (Get-NetTCPConnection -State Listen -LocalPort 9090 -ErrorAction SilentlyContinue) { throw 'Porta 9090 ocupada; preserve o Prometheus existente.' }
$promBinary = Join-Path $state 'tools/prometheus-3.5.0.windows-amd64/prometheus.exe'
if (-not (Test-Path $promBinary)) { throw 'Prometheus 3.5.0 local nao encontrado em .local/tools.' }
$run = Join-Path $state ('capacity-stack-' + (Get-Date -Format yyyyMMdd-HHmmss))
[void][IO.Directory]::CreateDirectory($run)
$debugBefore = $env:DEBUG
$otlpBefore = $env:MANAGEMENT_OTLP_METRICS_EXPORT_ENABLED
$env:DEBUG = 'false'
$env:MANAGEMENT_OTLP_METRICS_EXPORT_ENABLED = 'false'
$env:CAPACITY_DYNAMO_TIMEOUTS = 'api=10s,attempt=5s; somente emulador local'
$startedStack = $false
$promProcess = $null
Push-Location $root
try {
    # Host output inclui chave temporaria para Swagger; nao integra a evidencia.
    & (Join-Path $PSScriptRoot 'local-up.ps1') -PushgatewayAddress '' -OtlpTracesEndpoint '' -DynamoApiCallTimeout '10s' -DynamoApiCallAttemptTimeout '5s' 6>$null
    $startedStack = $true
    $config = [IO.Path]::GetFullPath((Join-Path $root '../infrastructure/Configuration/observability/config/prometheus/prometheus.local.yml'))
    $promArgs = @("--config.file=$config", "--storage.tsdb.path=$(Join-Path $run 'data')", '--web.listen-address=127.0.0.1:9090')
    $promProcess = Start-Process -FilePath $promBinary -ArgumentList $promArgs -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $run 'prometheus.out.log') -RedirectStandardError (Join-Path $run 'prometheus.err.log')
    if ($TestWorkerRestart) {
        node (Join-Path $PSScriptRoot 'durability-local.cjs')
        if ($LASTEXITCODE -ne 0) { throw 'Falha no ensaio de reinicio do worker.' }
    }
    node (Join-Path $PSScriptRoot 'capacity-local.cjs') $Seconds $Rates
    if ($LASTEXITCODE -ne 0) { throw 'Falha no gerador de capacidade.' }
    Start-Sleep -Seconds 20
    $evidence = @{}
    foreach ($query in @('up','slo:j1_availability_errors:ratio_rate5m','slo:j1_latency_100ms_errors:ratio_rate5m','ALERTS')) {
        $evidence[$query] = Invoke-RestMethod ('http://127.0.0.1:9090/api/v1/query?query=' + [Uri]::EscapeDataString($query))
    }
    $evidence | ConvertTo-Json -Depth 15 | Set-Content (Join-Path $run 'snapshot.json') -Encoding UTF8
} finally {
    if ($null -ne $promProcess -and -not $promProcess.HasExited) { Stop-Process -Id $promProcess.Id -Force }
    if ($startedStack) { & (Join-Path $PSScriptRoot 'local-down.ps1') }
    $env:DEBUG = $debugBefore
    $env:MANAGEMENT_OTLP_METRICS_EXPORT_ENABLED = $otlpBefore
    Remove-Item Env:CAPACITY_DYNAMO_TIMEOUTS -ErrorAction SilentlyContinue
    Pop-Location
}
