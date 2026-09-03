param(
    [Parameter(Mandatory = $true)][string]$PromptFile,
    [Parameter(Mandatory = $true)][string]$FinalReportFile,
    [string]$SessionId = 'not-recorded',
    [string]$Purpose = 'substantial Codex run'
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$targetDirectory = Join-Path $projectRoot 'docs\local\agent-sessions'
$promptPath = (Resolve-Path -LiteralPath $PromptFile).Path
$reportPath = (Resolve-Path -LiteralPath $FinalReportFile).Path
$prompt = Get-Content -Raw -LiteralPath $promptPath
$report = Get-Content -Raw -LiteralPath $reportPath
$content = $prompt + "`n" + $report

$secretPatterns = @(
    'DEEPSEEK_API_KEY\s*=\s*(?!PASTE_KEY_HERE|your_key_here)\S+',
    'Authorization:\s*Bearer\s+\S+',
    '-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----'
)
foreach ($pattern in $secretPatterns) {
    if ($content -match $pattern) {
        throw 'Potential secret detected. Evidence was not saved.'
    }
}

New-Item -ItemType Directory -Force -Path $targetDirectory | Out-Null
$timestamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$destination = Join-Path $targetDirectory "$timestamp-codex.md"
$branch = git -C $projectRoot branch --show-current
$head = git -C $projectRoot rev-parse HEAD
$body = @"
# Codex session evidence

- Recorded: $(Get-Date -Format 'o')
- Purpose: $Purpose
- Session: $SessionId
- Branch: $branch
- HEAD: $head

## Owner task

$prompt

## Final report

$report
"@
Set-Content -LiteralPath $destination -Value $body -Encoding utf8NoBOM
Write-Output $destination
