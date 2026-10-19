[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$Control,
    [Parameter(Mandatory = $true)][string]$Policy,
    [Parameter(Mandatory = $true)][string]$OutputDirectory,
    [string]$WorkerSnapshot
)
$ErrorActionPreference = 'Stop'
$appRoot = Split-Path -Parent $PSScriptRoot
if ([string]::IsNullOrWhiteSpace($WorkerSnapshot)) {
    $WorkerSnapshot = Join-Path $appRoot '.local/sre/worker-snapshot.json'
}
$classes = Join-Path $appRoot 'local-env/target/classes'
$libs = Join-Path $appRoot 'local-env/target/lib/*'
if (-not (Test-Path (Join-Path $classes 'br/com/consignado/platform/localenv/sre/SreCaptureMain.class'))) {
    throw 'Compile em applications: mvn -pl local-env -am package -DskipTests'
}
& java -cp "$classes;$libs" br.com.consignado.platform.localenv.sre.SreCaptureMain `
    ([IO.Path]::GetFullPath($Control)) ([IO.Path]::GetFullPath($Policy)) `
    ([IO.Path]::GetFullPath($WorkerSnapshot)) ([IO.Path]::GetFullPath($OutputDirectory))
exit $LASTEXITCODE
