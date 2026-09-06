$ErrorActionPreference = 'Stop'

$projectRoot = Split-Path -Parent $PSScriptRoot
$envFile = Join-Path $projectRoot '.env.local'

if (-not (Test-Path -LiteralPath $envFile -PathType Leaf)) {
    throw 'Missing .env.local. Copy .env.example and set DEEPSEEK_API_KEY.'
}

$keyLine = Get-Content -LiteralPath $envFile |
    Where-Object { $_ -match '^DEEPSEEK_API_KEY=' } |
    Select-Object -First 1

if (-not $keyLine) {
    throw 'DEEPSEEK_API_KEY is missing from .env.local.'
}

$apiKey = $keyLine.Substring('DEEPSEEK_API_KEY='.Length).Trim()
if ([string]::IsNullOrWhiteSpace($apiKey) -or $apiKey -eq 'PASTE_KEY_HERE') {
    throw 'DEEPSEEK_API_KEY is empty or still contains the placeholder.'
}

$previousKey = $env:DEEPSEEK_API_KEY
$previousOpenAiKey = $env:OPENAI_API_KEY
$openAiKeyLine = Get-Content -LiteralPath $envFile |
    Where-Object { $_ -match '^OPENAI_API_KEY=' } |
    Select-Object -First 1
$openAiKey = if ($openAiKeyLine) { $openAiKeyLine.Substring('OPENAI_API_KEY='.Length).Trim() } else { '' }
try {
    $env:DEEPSEEK_API_KEY = $apiKey
    if (-not [string]::IsNullOrWhiteSpace($openAiKey) -and $openAiKey -ne 'PASTE_KEY_HERE') {
        $env:OPENAI_API_KEY = $openAiKey
    }
    Push-Location $projectRoot
    try {
        & mvn -q spring-boot:run '-Dspring-boot.run.arguments=--server.port=18080'
        if ($LASTEXITCODE -ne 0) {
            throw "Backend exited with code $LASTEXITCODE."
        }
    } finally {
        Pop-Location
    }
} finally {
    $env:DEEPSEEK_API_KEY = $previousKey
    $env:OPENAI_API_KEY = $previousOpenAiKey
}
