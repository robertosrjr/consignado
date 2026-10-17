<# Verifica destinos locais de links Markdown inline; nao consulta URLs externas ou anchors. #>
[CmdletBinding()]
param()
$ErrorActionPreference = 'Stop'
$repoRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
Push-Location $repoRoot
try {
    $files = @(rg --files -g '*.md' -g '!**/target/**' -g '!**/.local/**' -g '!**/resultados/**')
    if ($LASTEXITCODE -ne 0) { throw 'Falha ao listar documentos com rg.' }
    $issues = @()
    foreach ($file in $files) {
        $full = Join-Path $repoRoot $file
        $lines = [IO.File]::ReadAllLines($full)
        for ($i = 0; $i -lt $lines.Count; $i++) {
            foreach ($match in [regex]::Matches($lines[$i], '\]\(([^\s\)]+)(?:\s+"[^"]*")?\)')) {
                $target = $match.Groups[1].Value.Trim('<', '>')
                if ($target -match '^(https?://|mailto:|app:|#)') { continue }
                $pathPart = [uri]::UnescapeDataString(($target -split '[#?]', 2)[0])
                if (-not $pathPart) { continue }
                $resolved = if ($pathPart.StartsWith('/')) {
                    Join-Path $repoRoot $pathPart.TrimStart('/')
                } else {
                    Join-Path (Split-Path $full) $pathPart
                }
                if (-not (Test-Path -LiteralPath $resolved)) {
                    $issues += "${file}:$($i + 1): $target"
                }
            }
        }
    }
    $issues | ForEach-Object { Write-Output $_ }
    Write-Output "Documentos: $($files.Count); destinos locais ausentes: $($issues.Count)."
    if ($issues.Count -gt 0) { exit 1 }
} finally {
    Pop-Location
}
