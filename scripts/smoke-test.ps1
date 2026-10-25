# TokenGate smoke test: chalte hue system pe saare main features check karta hai.
# Chalao: powershell -ExecutionPolicy Bypass -File scripts\smoke-test.ps1
param(
    [string]$BaseUrl = "http://localhost:8080",
    [string]$DashboardUrl = "http://localhost:3000",
    [string]$AdminToken = $env:TOKENGATE_ADMIN_TOKEN
)
if (-not $AdminToken) { $AdminToken = Read-Host "Admin token daalo" }

$script:pass = 0; $script:fail = 0
function Check($name, [scriptblock]$test) {
    try {
        if (& $test) { Write-Host "PASS  $name" -ForegroundColor Green; $script:pass++ }
        else { Write-Host "FAIL  $name" -ForegroundColor Red; $script:fail++ }
    } catch { Write-Host "FAIL  $name -> $($_.Exception.Message)" -ForegroundColor Red; $script:fail++ }
}
function Call($method, $path, $headers = @{}, $body = $null) {
    $p = @{ Uri = "$BaseUrl$path"; Method = $method; Headers = $headers; UseBasicParsing = $true }
    if ($body) { $p.ContentType = "application/json"; $p.Body = $body }
    try { Invoke-WebRequest @p }
    catch { $r = $_.Exception.Response; if ($r) { [pscustomobject]@{ StatusCode = [int]$r.StatusCode; Headers = @{}; Content = "" } } else { throw } }
}
function Body($r) {
    # Windows PowerShell 5.1 unknown content-types (actuator, openmetrics) ko bytes me deta hai
    if ($r.Content -is [byte[]]) { [Text.Encoding]::UTF8.GetString($r.Content) } else { $r.Content }
}
function NewTeam($prefix) {
    $name = "$prefix-" + (Get-Random -Maximum 999999)
    $r = Call POST "/admin/teams" @{ "X-Admin-Token" = $AdminToken } ('{"name":"' + $name + '"}')
    ($r.Content | ConvertFrom-Json)
}
function Chat($key, $prompt, $stream = $false) {
    $s = if ($stream) { ',"stream":true' } else { '' }
    Call POST "/v1/chat/completions" @{ Authorization = "Bearer $key" } ('{"model":"gpt-4o-mini"' + $s + ',"messages":[{"role":"user","content":"' + $prompt + '"}]}')
}

Write-Host "`n=== TokenGate smoke test ($BaseUrl) ===`n" -ForegroundColor Cyan
$admin = @{ "X-Admin-Token" = $AdminToken }

Check "App health is UP" { ((Body (Call GET "/actuator/health")) | ConvertFrom-Json).status -eq "UP" }

$team = NewTeam "smoke"
$key = $team.apiKey
$auth = @{ Authorization = "Bearer $key" }
Check "Admin can create a team (key starts with tg_)" { $key -like "tg_*" }

Check "Security: no key on /v1 -> 401" { (Call GET "/v1/usage/today").StatusCode -eq 401 }
Check "Security: team key on /admin -> 403" { (Call GET "/admin/teams" $auth).StatusCode -eq 403 }
Check "Security: wrong admin token -> 401" { (Call GET "/admin/teams" @{ "X-Admin-Token" = "hacker" }).StatusCode -eq 401 }

$unique = "smoke " + (Get-Random -Maximum 999999)
$first = Chat $key $unique
Check "Gateway: chat works via mock provider" { $first.StatusCode -eq 200 -and $first.Headers["X-TokenGate-Provider"] -eq "mock" }
Check "Cache: first call is a MISS" { $first.Headers["X-TokenGate-Cache"] -eq "MISS" }
Check "Cache: same call again is a HIT (Redis)" { (Chat $key $unique).Headers["X-TokenGate-Cache"] -eq "HIT" }

$stream = Chat $key ("stream " + $unique) $true
Check "Streaming: SSE chunks + [DONE]" { $stream.Content -match "chat.completion.chunk" -and $stream.Content -match "\[DONE\]" }

$usage = $null
for ($i = 0; $i -lt 30 -and -not ($usage -and $usage.requests -ge 2); $i++) {
    Start-Sleep -Milliseconds 700
    $usage = (Call GET "/v1/usage/today" $auth).Content | ConvertFrom-Json
}
Check "Kafka: usage + cost recorded by consumer (2 requests)" { $usage.requests -ge 2 -and [double]$usage.totalCostUsd -gt 0 }

Call PUT "/admin/teams/$($team.id)/budget" $admin '{"dailyBudgetUsd":0.000001}' | Out-Null
Check "Budget: over daily budget -> 429" { (Chat $key ("budget " + $unique)).StatusCode -eq 429 }

$limitKey = (NewTeam "smoke-limit").apiKey
$codes = 1..11 | ForEach-Object { (Chat $limitKey "limit test").StatusCode }
Check "Rate limit: 10 pass, 11th -> 429" { ($codes[0..9] | Where-Object { $_ -ne 200 }).Count -eq 0 -and $codes[10] -eq 429 }

Check "Dead letters API reachable (admin)" { (Call GET "/admin/dead-letters" $admin).StatusCode -eq 200 }
Check "Metrics: Prometheus has tokengate metrics" { (Body (Call GET "/actuator/prometheus")) -match "tokengate_gateway_requests_total" }

Check "Dashboard (nginx) is up" { (Invoke-WebRequest $DashboardUrl -UseBasicParsing).StatusCode -eq 200 }
Check "nginx does NOT expose /actuator" { -not ((Invoke-WebRequest "$DashboardUrl/actuator/prometheus" -UseBasicParsing).Content -match "tokengate_") }

$color = if ($script:fail -eq 0) { "Green" } else { "Red" }
Write-Host "`n=== $($script:pass) passed, $($script:fail) failed ===`n" -ForegroundColor $color
if ($script:fail -gt 0) { exit 1 }