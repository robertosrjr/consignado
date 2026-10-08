<#
.SYNOPSIS
  Sobe a PoC localmente SEM Docker: local-env (DynamoDB Local + ElasticMQ + emissor JWT + Dispatcher),
  mock-legacy, ingestion-api e worker, cada um em um processo Java.

.DESCRIPTION
  Pre-requisitos: Java 25 no PATH e o build feito (mvn -B -ntp -DskipTests package).
  Segredos (chave de API do legado e chave AES do payload) sao gerados a cada execucao e ficam em
  .local\env.json (fora do versionamento). Tudo escuta em 127.0.0.1.

  Portas: ingestion 8080 | mock-legacy 8081 | worker 8083 | DynamoDB 8000 | SQS 9324 | JWT 8082
#>
[CmdletBinding()]
param(
    [int]$LegacyCapacityTps = 180
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$state = Join-Path $root '.local'
$logs = Join-Path $state 'logs'
New-Item -ItemType Directory -Force -Path $logs | Out-Null

if (Test-Path (Join-Path $state 'pids.json')) {
    throw "Ja existe uma execucao registrada em .local\pids.json. Rode scripts\local-down.ps1 antes."
}

function Get-RandomBase64([int]$bytes) {
    $buffer = New-Object byte[] $bytes
    $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try { $rng.GetBytes($buffer) } finally { $rng.Dispose() }
    [Convert]::ToBase64String($buffer)
}

$jars = @{
    ingestion = Join-Path $root 'ingestion-api\target\app.jar'
    legacy    = Join-Path $root 'mock-legacy\target\app.jar'
    worker    = Join-Path $root 'worker\target\app.jar'
}
foreach ($jar in $jars.Values) {
    if (-not (Test-Path $jar)) { throw "Jar nao encontrado: $jar. Rode: mvn -B -ntp -DskipTests package" }
}
$localEnvLib = Join-Path $root 'local-env\target\lib'
$localEnvJar = Join-Path $root 'local-env\target\local-env-1.0.0-SNAPSHOT.jar'
$nativeLibs = Join-Path $root 'local-env\target\native-libs'
if (-not (Test-Path $localEnvJar) -or -not (Test-Path $localEnvLib)) {
    throw "local-env nao empacotado. Rode: mvn -B -ntp -DskipTests package"
}

$legacyApiKey = Get-RandomBase64 24
$payloadKey = Get-RandomBase64 32   # AES-256, a mesma chave para ingestion e worker

$dynamoUrl = 'http://127.0.0.1:8000'
$sqsUrl = 'http://127.0.0.1:9324'
$issuer = 'http://127.0.0.1:8082'

# Credenciais ficticias exigidas pelo SDK para falar com os emuladores locais (nunca reais).
$awsEnv = @{
    AWS_ACCESS_KEY_ID          = 'test'
    AWS_SECRET_ACCESS_KEY      = 'test'
    AWS_REGION                 = 'sa-east-1'
    AWS_ENDPOINT_URL_DYNAMODB  = $dynamoUrl
    AWS_ENDPOINT_URL_SQS       = $sqsUrl
}

$procs = @{}

function Start-Java {
    param([string]$Name, [string[]]$JavaArgs, [hashtable]$EnvVars)
    foreach ($key in $EnvVars.Keys) { [Environment]::SetEnvironmentVariable($key, $EnvVars[$key], 'Process') }
    $out = Join-Path $logs "$Name.out.log"
    $err = Join-Path $logs "$Name.err.log"
    $p = Start-Process -FilePath 'java' -ArgumentList $JavaArgs -PassThru -WindowStyle Hidden `
        -RedirectStandardOutput $out -RedirectStandardError $err
    $procs[$Name] = $p.Id
    Write-Host ("  {0,-12} pid {1}  (logs: .local\logs\{0}.*.log)" -f $Name, $p.Id)
}

function Wait-Port([int]$Port, [string]$Name, [int]$TimeoutSeconds = 90) {
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        if (Test-NetConnection -ComputerName 127.0.0.1 -Port $Port -InformationLevel Quiet -WarningAction SilentlyContinue) { return }
        Start-Sleep -Milliseconds 500
    }
    throw "$Name nao abriu a porta $Port em ${TimeoutSeconds}s. Veja .local\logs\"
}

try {
    Write-Host 'Subindo a PoC (sem Docker)...'

    # 1) local-env: DynamoDB Local, ElasticMQ, emissor JWT e Dispatcher
    Start-Java -Name 'local-env' -EnvVars $awsEnv -JavaArgs @(
        "-Dsqlite4java.library.path=$nativeLibs",
        '-cp', "$localEnvJar;$localEnvLib\*",
        'br.com.consignado.platform.localenv.LocalEnvMain')
    Wait-Port 8000 'DynamoDB Local'
    Wait-Port 9324 'ElasticMQ'
    Wait-Port 8082 'Emissor JWT'

    # 2) legado simulado
    Start-Java -Name 'mock-legacy' -EnvVars @{
        SERVER_PORT = '8081'; LEGACY_API_KEY = $legacyApiKey; LEGACY_CAPACITY_TPS = "$LegacyCapacityTps"
        LEGACY_API_DOCS_ENABLED = 'true'
    } -JavaArgs @('-jar', $jars.legacy)
    Wait-Port 8081 'mock-legacy'

    # 3) ingestion-api
    Start-Java -Name 'ingestion-api' -EnvVars ($awsEnv + @{
        SERVER_PORT = '8080'
        INGESTION_PAYLOAD_LOCAL_KEY = $payloadKey
        INGESTION_JWT_JWK_SET_URI = "$issuer/jwks"
        INGESTION_JWT_ISSUER = $issuer
        INGESTION_JWT_AUDIENCE = 'consignado-ingestion'
        INGESTION_API_DOCS_ENABLED = 'true'
    }) -JavaArgs @('-jar', $jars.ingestion)

    # 4) worker (URLs das filas: as mesmas que o local-env imprime)
    Start-Java -Name 'worker' -EnvVars ($awsEnv + @{
        SERVER_PORT = '8083'
        WORKER_LEGACY_BASE_URL = 'http://127.0.0.1:8081'
        WORKER_LEGACY_API_KEY = $legacyApiKey
        WORKER_PAYLOAD_LOCAL_KEY = $payloadKey
        WORKER_QUEUE_FAST_URL = "$sqsUrl/000000000000/sqs-fast-lane-queue"
        WORKER_QUEUE_STANDARD_URL = "$sqsUrl/000000000000/sqs-standard-lane-queue"
    }) -JavaArgs @('-jar', $jars.worker)

    Wait-Port 8080 'ingestion-api'
    Wait-Port 8083 'worker'

    $procs | ConvertTo-Json | Set-Content -Path (Join-Path $state 'pids.json') -Encoding utf8
    Write-Host ''
    Write-Host 'PoC no ar:'
    Write-Host '  Ingestion API  http://127.0.0.1:8080   Swagger: http://127.0.0.1:8080/swagger-ui.html'
    Write-Host '  Mock legacy    http://127.0.0.1:8081   Swagger: http://127.0.0.1:8081/swagger-ui.html'
    Write-Host '  Worker         http://127.0.0.1:8083/actuator/prometheus'
    Write-Host "  Token JWT      $issuer/token?tenant=banco-a"
    Write-Host "  Chave do legado (para o Swagger do mock): $legacyApiKey"
    Write-Host '  Encerrar: scripts\local-down.ps1'
}
catch {
    Write-Host "Falha ao subir: $($_.Exception.Message)" -ForegroundColor Red
    foreach ($id in $procs.Values) { Stop-Process -Id $id -Force -ErrorAction SilentlyContinue }
    throw
}
