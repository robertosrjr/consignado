[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][uri]$AlertmanagerUrl,
    [Parameter(Mandatory = $true)][string]$OutputDirectory,
    [ValidateRange(60, 900)][int]$LifetimeSeconds = 180,
    [switch]$PrepareOnly
)
$ErrorActionPreference = 'Stop'
if (-not $AlertmanagerUrl.IsAbsoluteUri -or $AlertmanagerUrl.Scheme -notin @('http','https') -or
    $AlertmanagerUrl.UserInfo -or $AlertmanagerUrl.Query -or $AlertmanagerUrl.Fragment -or
    $AlertmanagerUrl.AbsolutePath -ne '/') {
    throw 'Informe a URL base HTTP(S) do Alertmanager, sem credenciais, caminho ou query.'
}
$directory = [IO.Path]::GetFullPath($OutputDirectory)
if (Test-Path -LiteralPath $directory) { throw 'Escolha um diretorio novo para preservar os testes anteriores.' }
[void][IO.Directory]::CreateDirectory($directory)
$now = [DateTimeOffset]::UtcNow
$validationId = [guid]::NewGuid().ToString()
$alert = @{
    labels = @{
        alertname = 'ConsignadoNotificationTest'; notification = 'sre'; severity = 'warning'
        env = 'validation'; service = 'alerting'; job = 'alert-pipeline-test'; instance = 'notification-test'; test = 'true'
    }
    annotations = @{
        summary = 'TESTE CONTROLADO: notificacao SRE'
        description = "Validacao Alertmanager -> SNS. Nao representa falha da aplicacao. Expira em $LifetimeSeconds segundos."
        validation_id = $validationId
        runbook = 'ALERTAS.md#teste-controlado-de-notificacao'
        dashboard_path = '/d/consignado-sre-overview/consignado-sre-operacao'
    }
    startsAt = $now.ToString('o')
    endsAt = $now.AddSeconds($LifetimeSeconds).ToString('o')
}
$payload = ConvertTo-Json -InputObject @($alert) -Depth 6
$payloadFile = Join-Path $directory 'alert.json'
[IO.File]::WriteAllText($payloadFile, $payload, (New-Object Text.UTF8Encoding($false)))
if ($PrepareOnly) {
    Write-Output "Payload preparado sem envio: $payloadFile"
    return
}
$endpoint = $AlertmanagerUrl.AbsoluteUri.TrimEnd('/') + '/api/v2/alerts'
$response = Invoke-WebRequest -UseBasicParsing -Method Post -Uri $endpoint -InFile $payloadFile `
    -ContentType 'application/json; charset=utf-8' -TimeoutSec 15
@{
    validation_id = $validationId; httpStatus = $response.StatusCode
    submittedAt = [DateTimeOffset]::UtcNow.ToString('o')
    startsAt = $alert.startsAt; endsAt = $alert.endsAt
} | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $directory 'receipt.json') -Encoding utf8
Write-Output "Alerta de teste aceito pelo Alertmanager. validation_id=$validationId"
Write-Output "Expiracao automatica: $($alert.endsAt). Aguardar agrupamento para as notificacoes firing/resolved."
Write-Output 'HTTP 200 comprova apenas a entrada no Alertmanager. Verificar rota, silencios, inibicao e entrega SNS separadamente.'
