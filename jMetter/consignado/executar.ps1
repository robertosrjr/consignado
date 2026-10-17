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
    [switch]$Html
)
$ErrorActionPreference = 'Stop'
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
$argsJMeter = @('-n','-t',(Join-Path $PSScriptRoot 'Thread Group.jmx'),'-l',$jtl,'-j',$log,
    '-Jsmoke_threads=0','-Jload_threads=0','-Jstorm_threads=0','-Jbusiness_threads=0',
    "-Jhost=$ApiHost","-Jport=$ApiPort","-Jprotocol=$Protocol",
    "-Jtoken_host=$TokenHost","-Jtoken_port=$TokenPort","-Jtoken_protocol=$TokenProtocol",
    "-Jtenants=$Tenants",'-Jjmeter.save.saveservice.output_format=csv',
    '-Jjmeter.save.saveservice.print_field_names=true','-Jjmeter.save.saveservice.assertion_results_failure_message=true',
    '-Jjmeter.save.saveservice.successful=true','-Jjmeter.save.saveservice.label=true',
    '-Jsample_variables=tenant,key,amount,convenio,portability')
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
    & $JMeter @argsJMeter
    if ($LASTEXITCODE -ne 0) { throw "JMeter terminou com codigo $LASTEXITCODE. Log: $log" }
    if (-not (Test-Path -LiteralPath $jtl)) { throw "JTL nao foi gerado. Log: $log" }
    $failed = 0; $requests = 0; $samples = 0
    Import-Csv -LiteralPath $jtl | ForEach-Object {
        $samples++
        if ($_.success -ne 'true') { $failed++ }
        if ($_.label -like 'POST*') { $requests++ }
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
