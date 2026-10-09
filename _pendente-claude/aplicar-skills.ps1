# Aplica a revisao das skills e agentes em .claude/ (09/10/2026).
# - adiciona: skills borda-absorcao-invariantes e carga-caos-evidencia; agente garantia-auditor
# - remove:   skills service-modeling-skill e global-ai-principles (fora do escopo da PoC)
# - ajusta:   referencias as skills removidas e o escopo da docker-compose-spec-skill
# Uso, na raiz do repositorio:  powershell -ExecutionPolicy Bypass -File _pendente-claude\aplicar-skills.ps1

$ErrorActionPreference = 'Stop'
$root    = Split-Path -Parent $PSScriptRoot
$staging = $PSScriptRoot
$claude  = Join-Path $root '.claude'
$utf8    = New-Object System.Text.UTF8Encoding $false

if (-not (Test-Path (Join-Path $claude 'skills'))) { throw "Pasta .claude\skills nao encontrada em $root" }

# Troca um trecho preservando a quebra de linha do arquivo (LF ou CRLF). Use {NL} no texto novo.
function Replace-InFile($path, $old, $new) {
  $text = [System.IO.File]::ReadAllText($path, $utf8)
  $nl = if ($text.Contains("`r`n")) { "`r`n" } else { "`n" }
  $old = $old.Replace('{NL}', $nl)
  $new = $new.Replace('{NL}', $nl)
  if (-not $text.Contains($old)) { Write-Warning "Trecho nao encontrado em $path (talvez ja aplicado)"; return }
  [System.IO.File]::WriteAllText($path, $text.Replace($old, $new), $utf8)
  Write-Host "ajustado: $path"
}

# 1. Novas skills e agente
foreach ($skill in 'borda-absorcao-invariantes', 'carga-caos-evidencia', 'chaos-engineering') {
  Copy-Item -Recurse -Force (Join-Path $staging "skills\$skill") (Join-Path $claude 'skills')
  Write-Host "skill adicionada ou atualizada: $skill"
}
foreach ($agent in 'garantia-auditor.md', 'chaos-engineering-agent.md') {
  Copy-Item -Force (Join-Path $staging "agents\$agent") (Join-Path $claude 'agents')
  Write-Host "agente adicionado ou atualizado: $agent"
}

# 2. Skills fora do escopo
foreach ($skill in 'service-modeling-skill', 'global-ai-principles') {
  $p = Join-Path $claude "skills\$skill"
  if (Test-Path $p) { Remove-Item -Recurse -Force $p; Write-Host "skill removida: $skill" }
}

# 3. Referencias as skills removidas
Replace-InFile (Join-Path $claude 'agents\architecture-auditor.md') `
  '- **service-modeling-skill** → Bounded Contexts, TOGAF{NL}' ''
Replace-InFile (Join-Path $claude 'agents\README.md') `
  '- **architecture-auditor** → `architecture-guidance` + `service-modeling-skill`' `
  ('- **architecture-auditor** → `architecture-guidance`{NL}' +
   '- **garantia-auditor** → `borda-absorcao-invariantes` + `carga-caos-evidencia` (garantias da PoC: nenhuma proposta perdida, nenhuma averbação duplicada, teto de 180 TPS, QoS 70/30)')

# 4. Escopo da docker-compose-spec-skill
Replace-InFile (Join-Path $claude 'skills\docker-compose-spec-skill\SKILL.md') `
  'description: Habilidade especializada para projetar, validar e otimizar infraestruturas multicontêiner utilizando a Docker Compose Specification (compose.yaml). Use para criação de ambientes de dev/prod, otimização de builds, healthchecks e governança de containers.' `
  'description: "Use para projetar, validar e otimizar arquivos compose.yaml da stack de observabilidade (infrastructure/Configuration/observability, applications/compose.yaml) e de ambientes de CI, seguindo a Docker Compose Specification: builds, healthchecks e governança de containers. Não se aplica ao desenvolvimento local, que roda sem Docker (emuladores embarcados na JVM)."'

Write-Host ''
Write-Host 'Pronto. Confira com: git status .claude'
Write-Host "Depois, apague a pasta de staging: Remove-Item -Recurse -Force '$staging'"
