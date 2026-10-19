[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$Manifest,
    [Parameter(Mandatory = $true)][string]$Evidence,
    [Parameter(Mandatory = $true)][string]$OutputDirectory
)
$ErrorActionPreference = 'Stop'
$applicationsRoot = Split-Path -Parent $PSScriptRoot
$classes = Join-Path $applicationsRoot 'local-env/target/classes'
$libraries = Join-Path $applicationsRoot 'local-env/target/lib/*'
$reportClass = Join-Path $classes 'br/com/consignado/platform/localenv/sre/SreReportMain.class'
if (-not (Test-Path -LiteralPath $reportClass)) {
    throw 'Ferramenta nao compilada. Em applications: mvn -pl local-env -am package -DskipTests'
}
if (-not (Test-Path -LiteralPath (Join-Path $applicationsRoot 'local-env/target/lib'))) {
    throw 'Bibliotecas ausentes. Em applications: mvn -pl local-env -am package -DskipTests'
}
# Caminhos sao argumentos separados, sem construir codigo de shell.
& java -cp "$classes;$libraries" br.com.consignado.platform.localenv.sre.SreReportMain `
    ([IO.Path]::GetFullPath($Manifest)) ([IO.Path]::GetFullPath($Evidence)) ([IO.Path]::GetFullPath($OutputDirectory))
exit $LASTEXITCODE
