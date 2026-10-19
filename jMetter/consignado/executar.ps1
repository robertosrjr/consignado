param(
    [ValidateSet('Fumaca','Negocio','Carga','Duplicatas','Validar')][string]$Cenario = 'Fumaca',
    [string]$JMeter = 'C:\Developer\Programas\apache-jmeter-5.6.3\bin\jmeter.bat',
    [string]$JavaHome = 'C:\Developer\Programas\jdk\openjdk\openjdk-21.0.2_windows-x64_bin\jdk-21.0.2',
    [string]$ApiHost = '127.0.0.1',
    [ValidateRange(1,65535)][int]$ApiPort = 8080,
    [string]$TokenHost = '127.0.0.1',
    [ValidateRange(1,65535)][int]$TokenPort = 8082,
    [ValidateSet('http','https')][string]$Protocol = 'http',
    [ValidateSet('http','https')][string]$TokenProtocol = 'http',
    [ValidateRange(1,10000)][int]$Tenants = 20,
    [ValidateRange(1,100000)][int]$Quantidade = 10,
    [decimal]$Valor = 1000.00,
    [ValidateSet('INSS','ESOCIAL_CLT','SIAPE_PUBLICO')][string]$Convenio = 'INSS',
    [switch]$Portabilidade,
    [ValidateRange(1,100000)][int]$Threads = 20,
    [ValidateRange(1,100000)][int]$Rps = 50,
    [ValidateRange(1,86400)][int]$Duracao = 60,
    [ValidateRange(0,86400)][int]$Rampa = 10,
    [ValidateRange(1,60000)][int]$MaxMs = 100,
    [switch]$Html,
    [switch]$SreCapture,
    [switch]$SreFingerprint
)
$ErrorActionPreference = 'Stop'
$captureSession = $null
if ($SreFingerprint -and -not $SreCapture) { throw 'SreFingerprint exige SreCapture.' }
if ($SreCapture) {
    if ($ApiHost -notin @('127.0.0.1','localhost') -or $TokenHost -notin @('127.0.0.1','localhost')) {
        throw 'A captura SRE desta etapa suporta somente aplicacao local.'
    }
    $localState = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../applications/.local'))
    $sessionPids = Get-Content -Raw -LiteralPath (Join-Path $localState 'pids.json') | ConvertFrom-Json
    $environmentProcess = Get-Process -Id ([int]$sessionPids.'local-env') -ErrorAction Stop
    $workerCapture = Get-Content -Raw -LiteralPath (Join-Path $localState 'sre/worker-snapshot.json') | ConvertFrom-Json
    if (([DateTimeOffset]::UtcNow - [DateTimeOffset]::Parse($workerCapture.observedAt)).TotalSeconds -gt 5) {
        throw 'Snapshot do worker desatualizado. Inicie local-up.ps1 -SreCapture.'
    }
    $captureSession = @{
        workerInstanceId = [string]$workerCapture.instanceId
        localEnvPid = $environmentProcess.Id
        localEnvStartedAt = $environmentProcess.StartTime.ToUniversalTime().ToString('yyyy-MM-ddTHH:mm:ss.fffZ')
    }
}
if (-not (Test-Path -LiteralPath $JMeter)) { throw "JMeter nao encontrado: $JMeter" }
if (-not (Test-Path -LiteralPath (Join-Path $JavaHome 'bin\java.exe'))) { throw "Java nao encontrado em $JavaHome. Informe -JavaHome com um JDK compativel com o JMeter (ex.: 21)." }
if ($Valor -le 0 -or $Valor -ge 10000000000 -or [decimal]::Round($Valor,2) -ne $Valor) {
    throw 'Valor deve ser positivo, menor que 10 bilhoes e ter ate duas casas decimais.'
}
$resultDir = Join-Path $PSScriptRoot 'resultados'
[void][IO.Directory]::CreateDirectory($resultDir)
$run = $Cenario.ToLowerInvariant() + '-' + (Get-Date -Format 'yyyyMMdd-HHmmss-fff')
$jtl = Join-Path $resultDir ($run + '.jtl')
$log = Join-Path $resultDir ($run + '.log')
$sreSummaryFile = Join-Path $resultDir ($run + '.sre-summary.json')
$argsJMeter = @('-n','-t',(Join-Path $PSScriptRoot 'Thread Group.jmx'),'-l',$jtl,'-j',$log,
    '-Jsmoke_threads=0','-Jload_threads=0','-Jstorm_threads=0','-Jbusiness_threads=0',
    "-Jhost=$ApiHost","-Jport=$ApiPort","-Jprotocol=$Protocol",
    "-Jtoken_host=$TokenHost","-Jtoken_port=$TokenPort","-Jtoken_protocol=$TokenProtocol",
    "-Jtenants=$Tenants",'-Jjmeter.save.saveservice.output_format=csv',
    '-Jjmeter.save.saveservice.print_field_names=true','-Jjmeter.save.saveservice.assertion_results_failure_message=true',
    '-Jjmeter.save.saveservice.successful=true','-Jjmeter.save.saveservice.label=true',
    '-Jsampleresult.timestamp.start=true','-Jjmeter.save.saveservice.timestamp_format=ms',
    '-Jjmeter.save.saveservice.default_delimiter=,',
    '-Jsample_variables=tenant,key,amount,convenio,portability,sre_canonical,sre_response_kind,sre_eligible,sre_exclusion')
if ($SreCapture) { $argsJMeter += ('-Jsre_summary_file=' + $sreSummaryFile) }
switch ($Cenario) {
    'Fumaca' { $argsJMeter += @('-Jsmoke_threads=1',"-Jsmoke_loops=$Quantidade") }
    'Negocio' {
        $amount = $Valor.ToString('F2',[Globalization.CultureInfo]::InvariantCulture)
        $argsJMeter += @('-Jbusiness_threads=1',"-Jbusiness_requests=$Quantidade","-Jbusiness_amount=$amount",
            "-Jbusiness_convenio=$Convenio",('-Jbusiness_portability=' + $Portabilidade.IsPresent.ToString().ToLowerInvariant()))
        Write-Host ("Esperado na entrada: {0} propostas novas, {0} duplicatas, valor R$ {1}, ticket R$ {2}." -f $Quantidade,($Valor*$Quantidade),$Valor)
    }
    'Carga' {
        $argsJMeter += @("-Jload_threads=$Threads","-Jrps=$Rps","-Jduration=$Duracao","-Jramp=$Rampa","-Jmax_ms=$MaxMs")
        if ($Threads -lt $Tenants) { Write-Warning 'Threads < Tenants: apenas parte dos parceiros recebera carga.' }
        if ($Rps -gt ([Math]::Min($Threads,$Tenants)*100)) { Write-Warning 'Vazao pode ultrapassar a cota dos parceiros ativos (429).' }
    }
    'Duplicatas' {
        $argsJMeter += "-Jstorm_threads=$Threads"
        if ($Threads -gt 100) { Write-Warning 'Todos os disparos usam banco-001; a cota pode causar 429 e falhar o teste.' }
    }
    'Validar' { $argsJMeter += '-Jtenants=0' } # Apenas inicializacao; nenhuma chamada HTTP.
}
if ($Html) { $argsJMeter += @('-e','-o',(Join-Path $resultDir ($run + '-html'))) }
Push-Location $PSScriptRoot
$previousJavaHome = $env:JAVA_HOME
$previousJreHome = $env:JRE_HOME
$previousPath = $env:PATH
$previousLaunch = $env:JM_LAUNCH
try {
    $env:JAVA_HOME = $JavaHome
    $env:JRE_HOME = $JavaHome
    $env:PATH = (Join-Path $JavaHome 'bin') + ';' + $env:PATH
    $env:JM_LAUNCH = Join-Path $JavaHome 'bin\java.exe'
    $captureStartedAt = [DateTimeOffset]::UtcNow.ToString('o')
    & $JMeter @argsJMeter
    $jmeterExitCode = $LASTEXITCODE
    $captureEndedAt = [DateTimeOffset]::UtcNow.ToString('o')
    if ($jmeterExitCode -ne 0) { throw "JMeter terminou com codigo $jmeterExitCode. Log: $log" }
    if (-not (Test-Path -LiteralPath $jtl)) { throw "JTL nao foi gerado. Log: $log" }
    $failed = 0; $requests = 0; $samples = 0; $setupFailed = 0
    Import-Csv -LiteralPath $jtl | ForEach-Object {
        $samples++
        if ($_.success -ne 'true') { $failed++ }
        if ($_.label -like 'POST*') { $requests++ }
        elseif ($_.label -in @('GET /token','Valida parametros e inicia contadores') -and $_.success -ne 'true') { $setupFailed++ }
    }
    if ($SreCapture) {
        $sreSummary = if (Test-Path -LiteralPath $sreSummaryFile) { Get-Content -Raw -LiteralPath $sreSummaryFile | ConvertFrom-Json } else { $null }
        $expectedRequests = switch ($Cenario) {
            'Fumaca' { $Quantidade }
            'Negocio' { 2 * $Quantidade }
            'Duplicatas' { $Threads }
            'Validar' { 0 }
            'Carga' { if ($null -ne $sreSummary) { [long]$sreSummary.attempts } else { -1 } }
        }
        $captureControl = @{
            schemaVersion = 1; jtlFile = [IO.Path]::GetFullPath($jtl)
            jtlSha256 = (Get-FileHash -LiteralPath $jtl -Algorithm SHA256).Hash.ToLowerInvariant()
            summaryFile = $sreSummaryFile
            summarySha256 = if ($null -ne $sreSummary) { (Get-FileHash -LiteralPath $sreSummaryFile -Algorithm SHA256).Hash.ToLowerInvariant() } else { '' }
            startedAt = $captureStartedAt; endedAt = $captureEndedAt
            clientComplete = ($setupFailed -eq 0 -and $samples -gt 0 -and $requests -eq $expectedRequests -and
                $null -ne $sreSummary -and $sreSummary.completed -and $sreSummary.attempts -eq $requests)
            expectedAttempts = $expectedRequests
            workerInstanceId = $captureSession.workerInstanceId
            localEnvPid = $captureSession.localEnvPid; localEnvStartedAt = $captureSession.localEnvStartedAt
            fingerprintEnabled = $SreFingerprint.IsPresent
        }
        $controlFile = Join-Path $resultDir ($run + '.sre-control.json')
        $captureControl | ConvertTo-Json | Set-Content -LiteralPath $controlFile -Encoding utf8
        Write-Host "Controle de captura SRE: $controlFile"
    }
    if ($samples -eq 0) { throw "Nenhuma amostra executada; verifique compilacao Groovy/JDK. Log: $log" }
    if ($failed -gt 0) { throw "$failed amostra(s) com falha. JTL: $jtl; log: $log" }
    if ($Cenario -ne 'Validar' -and $requests -eq 0) { throw "Nenhuma proposta enviada. Log: $log" }
    Write-Host "Validacao concluida. POSTs: $requests. JTL: $jtl"
    Write-Host "Log: $log"
} finally {
    $env:JAVA_HOME = $previousJavaHome
    $env:JRE_HOME = $previousJreHome
    $env:PATH = $previousPath
    $env:JM_LAUNCH = $previousLaunch
    Pop-Location
}
