param([string]$BaseUrl = 'http://localhost:18080')

$ErrorActionPreference = 'Stop'
$benchmark = @'
Проанализируй Java-код и найди инженерные риски. Не предполагай скрытые гарантии, которых нет в snippet.

```java
@Transactional
public void handle(PaymentReceived event) {
    Order order = orders.findById(event.orderId()).orElseThrow();
    order.markPaid();
    email.sendReceipt(order.customerEmail());
}
```
'@
$strategies = @('DIRECT', 'STEP_BY_STEP', 'SELF_PROMPT', 'EXPERTS')
$results = @()

foreach ($run in 1..3) {
    foreach ($strategy in $strategies) {
        $body = @{ input = $benchmark; strategy = $strategy } | ConvertTo-Json
        $response = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/reasoning-review" `
            -ContentType 'application/json; charset=utf-8' -Body ([Text.Encoding]::UTF8.GetBytes($body))
        if ([string]::IsNullOrWhiteSpace($response.analysis) -or $response.strategy -ne $strategy) {
            throw "Invalid $strategy response in run $run."
        }
        if ($strategy -eq 'SELF_PROMPT' -and [string]::IsNullOrWhiteSpace($response.generatedPrompt)) {
            throw "SELF_PROMPT run $run did not return a generated prompt."
        }
        $results += [pscustomobject]@{ run = $run; strategy = $strategy; response = $response }
        Write-Output "PASS run=$run strategy=$strategy"
    }
}

$projectRoot = Split-Path -Parent $PSScriptRoot
$evidenceDirectory = Join-Path $projectRoot 'docs\local\agent-sessions'
New-Item -ItemType Directory -Force -Path $evidenceDirectory | Out-Null
$evidencePath = Join-Path $evidenceDirectory "day3-api-$(Get-Date -Format 'yyyyMMdd-HHmmss').json"
$results | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $evidencePath -Encoding utf8NoBOM
Write-Output "Saved local evidence: $evidencePath"
