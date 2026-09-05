$ErrorActionPreference = 'Stop'

$projectRoot = Split-Path -Parent $PSScriptRoot
$evidenceDirectory = Join-Path $projectRoot 'docs/local/agent-sessions'
$evidencePath = Join-Path $evidenceDirectory ("day4-temperature-{0}.json" -f (Get-Date -Format 'yyyyMMdd-HHmmss'))
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

New-Item -ItemType Directory -Path $evidenceDirectory -Force | Out-Null
$results = foreach ($run in 1..3) {
    foreach ($temperature in @(0, 0.7, 1.2)) {
        $body = @{ input = $benchmark; temperature = $temperature } | ConvertTo-Json
        $response = Invoke-RestMethod -Method Post -Uri 'http://localhost:18080/api/temperature-review' -ContentType 'application/json; charset=utf-8' -Body $body
        if ([string]::IsNullOrWhiteSpace($response.analysis) -or $response.temperature -ne $temperature) {
            throw "Day 4 verification failed for run $run, temperature $temperature."
        }
        [pscustomobject]@{ run = $run; temperature = $temperature; input = $benchmark; analysis = $response.analysis }
        Write-Host "PASS run=$run temperature=$temperature"
    }
}

$results | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath $evidencePath -Encoding utf8
Write-Host "Saved ignored evidence: $evidencePath"
