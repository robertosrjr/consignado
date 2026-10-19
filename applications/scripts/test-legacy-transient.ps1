<# Ensaios locais: L1 (503 transitorio) e L2 (timeout com commit tardio). #>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$OutputDirectory,
    [ValidateSet('L1','L2')][string]$Scenario = 'L1',
    [string]$JMeter = 'C:\Developer\Programas\apache-jmeter-5.6.3\bin\jmeter.bat',
    [string]$JavaHome = 'C:\Developer\Programas\jdk\openjdk\openjdk-21.0.2_windows-x64_bin\jdk-21.0.2'
)
$ErrorActionPreference = 'Stop'
$quantity = if ($Scenario -eq 'L1') { 5 } else { 1 }
$expectedRequests = 2 * $quantity
$faultLatencyMs = if ($Scenario -eq 'L1') { 0 } else { 4000 }
$faultErrorRate = if ($Scenario -eq 'L1') { 1 } else { 0 }
$appRoot = Split-Path -Parent $PSScriptRoot
$repoRoot = Split-Path -Parent $appRoot
$source = Join-Path $repoRoot 'jMetter/consignado'
$output = [IO.Path]::GetFullPath($OutputDirectory)
if (Test-Path -LiteralPath $output) { throw 'Diretorio de evidencia deve ser novo.' }
if (-not (Test-Path -LiteralPath $JMeter) -or -not (Test-Path (Join-Path $JavaHome 'bin/java.exe'))) {
    throw 'JMeter ou Java 21 indisponivel.'
}
if ([string]::IsNullOrWhiteSpace($env:LEGACY_FAULT_CONTROL_TOKEN) -or $env:LEGACY_FAULT_CONTROL_TOKEN.Length -lt 32) {
    throw 'Token de controle indisponivel.'
}
$headers = @{ 'X-SRE-Control-Token' = $env:LEGACY_FAULT_CONTROL_TOKEN }
$controlUri = 'http://127.0.0.1:8081/internal/sre/fault'
foreach ($port in @(8080,8081,8083)) {
    $health = Invoke-RestMethod -Uri "http://127.0.0.1:$port/actuator/health" -TimeoutSec 5
    if ($health.status -ne 'UP') { throw "Servico $port nao esta saudavel." }
}
$neutral = Invoke-RestMethod -Uri $controlUri -Headers $headers -TimeoutSec 5
if ($neutral.active -or $neutral.latencyMs -ne 0 -or $neutral.errorRate -ne 0) { throw 'Baseline de caos nao neutra.' }
$snapshotPath = Join-Path $appRoot '.local/sre/worker-snapshot.json'
function Read-Snapshot {
    for ($attempt = 0; $attempt -lt 10; $attempt++) {
        try {
            $stream = [IO.File]::Open($snapshotPath, [IO.FileMode]::Open, [IO.FileAccess]::Read,
                ([IO.FileShare]::ReadWrite -bor [IO.FileShare]::Delete))
            try {
                $reader = [IO.StreamReader]::new($stream)
                try { return ($reader.ReadToEnd() | ConvertFrom-Json) } finally { $reader.Dispose() }
            } finally { $stream.Dispose() }
        } catch [IO.IOException] {
            if ($attempt -eq 9) { throw }
            Start-Sleep -Milliseconds 50
        }
    }
}
$snapshot = Read-Snapshot
$workerInstanceId = [string]$snapshot.instanceId
if (([DateTimeOffset]::UtcNow - [DateTimeOffset]::Parse($snapshot.observedAt)).TotalSeconds -gt 5) {
    throw 'Snapshot desatualizado.'
}
$pids = Get-Content -Raw -LiteralPath (Join-Path $appRoot '.local/pids.json') | ConvertFrom-Json
$environmentProcess = Get-Process -Id ([int]$pids.'local-env') -ErrorAction Stop

function Get-Queues {
    $result = @()
    foreach ($name in @('sqs-fast-lane-queue','sqs-standard-lane-queue','sqs-dlq')) {
        $json = & aws --endpoint-url http://127.0.0.1:9324 --region sa-east-1 --no-sign-request sqs get-queue-attributes `
            --queue-url "http://127.0.0.1:9324/000000000000/$name" --attribute-names All --output json
        if ($LASTEXITCODE -ne 0) { throw 'Falha na leitura das filas locais.' }
        $attributes = ($json | ConvertFrom-Json).Attributes
        $result += [pscustomobject]@{ queue = $name; attributes = $attributes }
    }
    return $result
}
function Assert-Empty($queues) {
    foreach ($queue in $queues) {
        foreach ($name in @('ApproximateNumberOfMessages','ApproximateNumberOfMessagesNotVisible','ApproximateNumberOfMessagesDelayed')) {
            if ($null -eq $queue.attributes.$name -or [long]$queue.attributes.$name -ne 0) {
                throw 'Fila com trabalho pendente ou cobertura incompleta; isolar a coorte antes do teste.'
            }
        }
    }
}
function Get-Metric([string]$text, [string]$name, [string]$tag, [string]$value) {
    $pattern = '(?m)^' + [regex]::Escape($name) + '\{[^\r\n}]*' + $tag + '="' + $value + '"[^\r\n}]*\}\s+([0-9.eE+\-]+)\s*$'
    $found = [regex]::Matches($text, $pattern)
    if ($found.Count -ne 1) { throw "Serie ausente ou ambigua: $name/$value" }
    return [double]::Parse($found[0].Groups[1].Value, [Globalization.CultureInfo]::InvariantCulture)
}
$queuesBefore = Get-Queues
Assert-Empty $queuesBefore
$beforeMetrics = (Invoke-WebRequest -Uri http://127.0.0.1:8081/actuator/prometheus -UseBasicParsing -TimeoutSec 5).Content
[void][IO.Directory]::CreateDirectory($output)
Copy-Item -LiteralPath (Join-Path $source 'gera-negocio.groovy'),(Join-Path $source 'gera-proposta.groovy'),(Join-Path $source 'valida-proposta.groovy') -Destination $output
$plan = [xml][IO.File]::ReadAllText((Join-Path $source 'Thread Group.jmx'))
$tree = $plan.SelectSingleNode('//HTTPSamplerProxy[@testname="POST negocio (nova)"]/following-sibling::hashTree[1]')
if ($null -eq $tree) { throw 'Sampler de negocio nao encontrado; plano deve ser revisado.' }
$hook = $plan.CreateElement('JSR223PreProcessor')
foreach ($attribute in @{ guiclass='TestBeanGUI'; testclass='JSR223PreProcessor'; testname="$Scenario`: falha temporaria antes da primeira proposta"; enabled='true' }.GetEnumerator()) {
    $hook.SetAttribute($attribute.Key, $attribute.Value)
}
$groovy = @'
if (props.putIfAbsent('sre_legacy_activation_attempted', true) == null) {
    String token = System.getenv('LEGACY_FAULT_CONTROL_TOKEN')
    assert token && token.length() >= 32 : 'controle indisponivel'
    def connection = new URL('http://127.0.0.1:8081/internal/sre/fault').openConnection()
    connection.setInstanceFollowRedirects(false)
    connection.setRequestMethod('POST')
    connection.setConnectTimeout(2000)
    connection.setReadTimeout(2000)
    connection.setDoOutput(true)
    connection.setRequestProperty('X-SRE-Control-Token', token)
    connection.setRequestProperty('Content-Type', 'application/json')
    String start = java.time.Instant.now().toString()
    def fault = [latencyMs:props.getProperty('sre_fault_latency_ms') as long, errorRate:props.getProperty('sre_fault_error_rate') as double, durationMs:2000L]
    connection.outputStream.withCloseable { it.write(groovy.json.JsonOutput.toJson(fault).getBytes('UTF-8')) }
    assert connection.responseCode == 200 : 'ativacao recusada'
    def state = new groovy.json.JsonSlurper().parseText(connection.inputStream.getText('UTF-8'))
    assert state.active : 'ativacao nao observada'
    new File(props.getProperty('sre_legacy_activation_file')).setText(groovy.json.JsonOutput.toJson([startedAt:start,observedAt:java.time.Instant.now().toString(),state:state]), 'UTF-8')
    props.put('sre_legacy_activated', true)
    connection.disconnect()
}
'@
foreach ($entry in @{scriptLanguage='groovy'; parameters=''; filename=''; cacheKey='true'; script=$groovy}.GetEnumerator()) {
    $property = $plan.CreateElement('stringProp'); $property.SetAttribute('name', $entry.Key); $property.InnerText = $entry.Value
    [void]$hook.AppendChild($property)
}
[void]$tree.AppendChild($hook)
[void]$tree.AppendChild($plan.CreateElement('hashTree'))
$planPath = Join-Path $output ("legacy-" + $Scenario.ToLowerInvariant() + '.jmx')
# Indentacao automatica pode transformar filename vazio em whitespace no JMeter.
foreach ($filename in $plan.SelectNodes('//stringProp[@name="filename"]')) {
    if ([string]::IsNullOrWhiteSpace($filename.InnerText)) { $filename.IsEmpty = $true }
}
$xmlSettings = New-Object System.Xml.XmlWriterSettings
$xmlSettings.Indent = $false
$xmlSettings.Encoding = [Text.UTF8Encoding]::new($false)
$writer = [System.Xml.XmlWriter]::Create($planPath, $xmlSettings)
try { $plan.Save($writer) } finally { $writer.Dispose() }
$jtl = Join-Path $output 'requests.jtl'
$summary = Join-Path $output 'client-summary.json'
$activation = Join-Path $output 'activation.json'
$previousJava = $env:JAVA_HOME; $previousJre = $env:JRE_HOME; $previousPath = $env:PATH
$previousLaunch = $env:JM_LAUNCH
$jmeterProcess = $null
$startedAt = [DateTimeOffset]::UtcNow.ToString('o')
$result = [ordered]@{ scenario=$Scenario; status='INCOMPLETE'; startedAt=$startedAt; queuesBefore=$queuesBefore }
try {
    $env:JAVA_HOME=$JavaHome; $env:JRE_HOME=$JavaHome; $env:PATH=(Join-Path $JavaHome 'bin') + ';' + $env:PATH
    $env:JM_LAUNCH='java'
    $jmeterArgs = @(
        '-n','-t',$planPath,'-l',$jtl,'-j',(Join-Path $output 'jmeter.log'),
        '-Jsmoke_threads=0','-Jload_threads=0','-Jstorm_threads=0','-Jbusiness_threads=1',"-Jbusiness_requests=$quantity",
        '-Jbusiness_amount=1000.00','-Jbusiness_convenio=INSS','-Jbusiness_portability=false','-Jtenants=1',
        '-Jhost=127.0.0.1','-Jport=8080','-Jtoken_host=127.0.0.1','-Jtoken_port=8082',
        '-Jjmeter.save.saveservice.output_format=csv','-Jjmeter.save.saveservice.print_field_names=true',
        '-Jjmeter.save.saveservice.default_delimiter=,','-Jjmeter.save.saveservice.timestamp_format=ms',
        '-Jsampleresult.timestamp.start=true','-Jsample_variables=tenant,key,amount,convenio,portability,sre_canonical,sre_response_kind,sre_eligible,sre_exclusion',
        "-Jsre_summary_file=$summary","-Jsre_legacy_activation_file=$activation",
        "-Jsre_fault_latency_ms=$faultLatencyMs","-Jsre_fault_error_rate=$faultErrorRate"
    )
    # Cliente pequeno e processo proprio: nao reservar o heap padrao de 1 GB para duas chamadas.
    $javaArgs = @('-Xms128m','-Xmx512m','-jar',(Join-Path (Split-Path -Parent $JMeter) 'ApacheJMeter.jar')) + $jmeterArgs
    $quotedArgs = @($javaArgs | ForEach-Object { '"' + $_ + '"' })
    $jmeterProcess = Start-Process -FilePath (Join-Path $JavaHome 'bin/java.exe') -ArgumentList $quotedArgs `
        -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $output 'jmeter-console.log') `
        -RedirectStandardError (Join-Path $output 'jmeter-stderr.log')
    $result.clientPid = $jmeterProcess.Id
    # Manter o handle aberto: no Windows, ExitCode pode ficar null se o handle for obtido tarde.
    $clientHandle = $jmeterProcess.Handle
    $clientDeadline = [DateTimeOffset]::UtcNow.AddSeconds(180)
    while (-not $jmeterProcess.WaitForExit(1000)) {
        if ([DateTimeOffset]::UtcNow -gt $clientDeadline) {
            Stop-Process -Id $jmeterProcess.Id -Force -ErrorAction SilentlyContinue
            $result.clientAborted = $true
            throw 'JMeter excedeu 180 segundos; processo deste ensaio interrompido.'
        }
    }
    $jmeterProcess.WaitForExit()
    $result.jmeterExit = $jmeterProcess.ExitCode
    $endedAt = [DateTimeOffset]::UtcNow.ToString('o')
    $result.endedAt = $endedAt
    $result.faultAfterClient = Invoke-RestMethod -Uri $controlUri -Headers $headers -TimeoutSec 5
    if ($result.jmeterExit -ne 0 -or -not (Test-Path -LiteralPath $jtl)) { throw 'JMeter nao concluiu; consultar log local.' }
    $requests = @(Import-Csv -LiteralPath $jtl | Where-Object { $_.label -like 'POST*' })
    if ($requests.Count -ne $expectedRequests -or -not (Test-Path -LiteralPath $summary)) { throw 'Setup ou massa incompletos; consultar JTL e log.' }
    $result.http = @($requests | Select-Object timeStamp,elapsed,label,responseCode,success,key,sre_canonical,sre_response_kind)
    $ids = @($requests | Where-Object { $_.sre_response_kind -eq 'RECEIVED' } | Select-Object -ExpandProperty sre_canonical -Unique)
    $deadline = [DateTimeOffset]::UtcNow.AddSeconds(60)
    $observations = @()
    do {
        $snapshot = Read-Snapshot
        if ($snapshot.instanceId -ne $workerInstanceId) { throw 'Sessao alterada.' }
        if (([DateTimeOffset]::UtcNow - [DateTimeOffset]::Parse($snapshot.observedAt)).TotalSeconds -gt 5) { throw 'Snapshot perdeu frescor.' }
        $cohort = @($snapshot.states | Where-Object { $_.canonicalId -in $ids })
        if ($Scenario -eq 'L2') {
            $pollMetrics = (Invoke-WebRequest -Uri http://127.0.0.1:8081/actuator/prometheus -UseBasicParsing -TimeoutSec 5).Content
            $observations += [pscustomobject]@{
                workerObservedAt=$snapshot.observedAt; metricObservedAt=[DateTimeOffset]::UtcNow.ToString('o')
                states=@($cohort | Select-Object canonicalId,status,committedAt)
                uniqueEffects=(Get-Metric $pollMetrics 'legacy_averbacoes_total' 'kind' 'unique') - (Get-Metric $beforeMetrics 'legacy_averbacoes_total' 'kind' 'unique')
                replayedLegacy=(Get-Metric $pollMetrics 'legacy_averbacoes_total' 'kind' 'duplicate') - (Get-Metric $beforeMetrics 'legacy_averbacoes_total' 'kind' 'duplicate')
            }
        }
        if (@($cohort | Where-Object { $_.status -eq 'CONFIRMED' }).Count -eq $quantity) { break }
        Start-Sleep -Milliseconds 500
    } while ([DateTimeOffset]::UtcNow -lt $deadline)
    $result.worker = $cohort
    $result.observations = $observations
    $result.workerObservedAt = $snapshot.observedAt
    $afterMetrics = (Invoke-WebRequest -Uri http://127.0.0.1:8081/actuator/prometheus -UseBasicParsing -TimeoutSec 5).Content
    $result.chaosFailures = (Get-Metric $afterMetrics 'legacy_requests_total' 'outcome' 'failed_chaos') - (Get-Metric $beforeMetrics 'legacy_requests_total' 'outcome' 'failed_chaos')
    $result.uniqueEffects = (Get-Metric $afterMetrics 'legacy_averbacoes_total' 'kind' 'unique') - (Get-Metric $beforeMetrics 'legacy_averbacoes_total' 'kind' 'unique')
    $result.replayedLegacy = (Get-Metric $afterMetrics 'legacy_averbacoes_total' 'kind' 'duplicate') - (Get-Metric $beforeMetrics 'legacy_averbacoes_total' 'kind' 'duplicate')
    $result.queuesAfter = Get-Queues
    Assert-Empty $result.queuesAfter
    $result.activationObserved = Test-Path -LiteralPath $activation
    $result.faultBeforeRestore = Invoke-RestMethod -Uri $controlUri -Headers $headers -TimeoutSec 5
    $faultExercised = if ($Scenario -eq 'L1') { $result.chaosFailures -gt 0 -and $result.replayedLegacy -eq 0 } else { $result.chaosFailures -eq 0 -and $result.replayedLegacy -eq 1 }
    $result.status = if ($result.jmeterExit -eq 0 -and $requests.Count -eq $expectedRequests -and
        @($requests | Where-Object { $_.responseCode -ne '202' }).Count -eq 0 -and $ids.Count -eq $quantity -and
        @($requests | Where-Object { $_.sre_response_kind -eq 'DUPLICATE_ACCEPTED' }).Count -eq $quantity -and
        @($cohort | Where-Object { $_.status -eq 'CONFIRMED' }).Count -eq $quantity -and
        $faultExercised -and $result.uniqueEffects -eq $quantity -and
        $result.activationObserved -and -not $result.faultBeforeRestore.active) { 'PASS' } else { 'FAIL' }
    $clientSummary = Get-Content -Raw -LiteralPath $summary | ConvertFrom-Json
    $capture = @{
        schemaVersion=1; jtlFile=$jtl; jtlSha256=(Get-FileHash $jtl -Algorithm SHA256).Hash.ToLowerInvariant()
        summaryFile=$summary; summarySha256=(Get-FileHash $summary -Algorithm SHA256).Hash.ToLowerInvariant()
        startedAt=$startedAt; endedAt=$endedAt; expectedAttempts=$expectedRequests
        clientComplete=($requests.Count -eq $expectedRequests -and $clientSummary.completed -and $clientSummary.attempts -eq $expectedRequests)
        workerInstanceId=$workerInstanceId; localEnvPid=$environmentProcess.Id
        localEnvStartedAt=$environmentProcess.StartTime.ToUniversalTime().ToString('yyyy-MM-ddTHH:mm:ss.fffZ'); fingerprintEnabled=$false
    }
    $capture | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $output 'sre-control.json') -Encoding UTF8
} finally {
    if ($null -ne $jmeterProcess) {
        if (-not $jmeterProcess.HasExited) { Stop-Process -Id $jmeterProcess.Id -Force -ErrorAction SilentlyContinue }
        $jmeterProcess.Dispose()
    }
    $env:JAVA_HOME=$previousJava; $env:JRE_HOME=$previousJre; $env:PATH=$previousPath
    $env:JM_LAUNCH=$previousLaunch
    try {
        $result.restored = Invoke-RestMethod -Uri $controlUri -Method Delete -Headers $headers -TimeoutSec 5
        if ($result.restored.active -or $result.restored.latencyMs -ne 0 -or $result.restored.errorRate -ne 0) {
            $result.status='INCOMPLETE'; $result.restoreFailed=$true
        }
    }
    catch { $result.status='INCOMPLETE'; $result.restoreFailed=$true }
    $result.finishedAt = [DateTimeOffset]::UtcNow.ToString('o')
    $result | ConvertTo-Json -Depth 12 | Set-Content -LiteralPath (Join-Path $output 'result.json') -Encoding UTF8
}
Write-Output "$Scenario`: $($result.status); evidencia: $output"
if ($result.status -ne 'PASS') { exit 1 }
