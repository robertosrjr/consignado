<# Verifica destinos locais de links Markdown inline; nao consulta URLs externas ou anchors. #>
[CmdletBinding()]
param()
$ErrorActionPreference = 'Stop'
$repoRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
Push-Location $repoRoot
try {
    $files = @(rg --files --hidden -g '*.md' -g '!**/.git/**' -g '!**/target/**' -g '!**/.local/**' -g '!**/resultados/**')
    if ($LASTEXITCODE -ne 0) { throw 'Falha ao listar documentos com rg.' }
    $issues = @()
    foreach ($file in $files) {
        $full = Join-Path $repoRoot $file
        $lines = [IO.File]::ReadAllLines($full)
        $insideFence = $false
        for ($i = 0; $i -lt $lines.Count; $i++) {
            if ($lines[$i] -match '^\s*(```|~~~)') {
                $insideFence = -not $insideFence
                continue
            }
            if ($insideFence) { continue }
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
