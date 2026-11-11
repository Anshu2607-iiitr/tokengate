# Semantic cache: before/after load test, ek command me.
# Chalao: powershell -ExecutionPolicy Bypass -File loadtest\run-comparison.ps1
Set-Location (Split-Path $PSScriptRoot -Parent)

function Info($m) { Write-Host "[..] $m" -ForegroundColor Yellow }
function Ok($m)   { Write-Host "[OK] $m" -ForegroundColor Green }
function Fail($m) { Write-Host "[!!] $m" -ForegroundColor Red; exit 1 }

function Wait-App {
    for ($i = 0; $i -lt 60; $i++) {
        try { if ((Invoke-RestMethod http://localhost:8080/actuator/health -TimeoutSec 3).status -eq "UP") { return } } catch { }
        Start-Sleep 3
    }
    Fail "App UP nahi hui. Dekho: docker compose logs app --tail 50"
}

Write-Host "`n=== Semantic cache load test: A (OFF) vs B (ON) ===`n" -ForegroundColor Cyan

# 1. Checks
if (-not (Get-Command k6 -ErrorAction SilentlyContinue)) { Fail "k6 nahi mila. Install: winget install k6 --accept-source-agreements (phir naya terminal)" }
if (-not (Test-Path "loadtest\semantic-loadtest.js")) { Fail "loadtest\semantic-loadtest.js nahi mili" }
$line = Select-String -Path .env -Pattern "^TOKENGATE_ADMIN_TOKEN=(.+)$" | Select-Object -First 1
if (-not $line) { Fail ".env me admin token nahi. Pehle scripts\start.ps1 chalao" }
$admin = @{ "X-Admin-Token" = $line.Matches[0].Groups[1].Value.Trim() }
try {
    Invoke-RestMethod -Uri http://localhost:11434/v1/embeddings -Method Post -ContentType "application/json" -Body '{"model":"nomic-embed-text","input":"test"}' -TimeoutSec 30 | Out-Null
    Ok "Ollama embeddings ready"
} catch { Fail "Ollama embeddings nahi chal rahe. Ollama app kholo aur chalao: ollama pull nomic-embed-text" }

# 2. Rate limit temporarily high
$compose = [IO.File]::ReadAllText("$PWD\docker-compose.yml")
if ($compose -notmatch "RATE_LIMIT") {
    $compose = $compose.Replace('      TOKENGATE_ADMIN_TOKEN: ${TOKENGATE_ADMIN_TOKEN:-dev-admin-secret}', "      TOKENGATE_ADMIN_TOKEN: `${TOKENGATE_ADMIN_TOKEN:-dev-admin-secret}`n      TOKENGATE_RATE_LIMIT_REQUESTS_PER_MINUTE: `${TOKENGATE_RATE_LIMIT:-10}")
    [IO.File]::WriteAllText("$PWD\docker-compose.yml", $compose)
}
Info "Rate limit temporarily badha raha hoon (app restart, ~30s)..."
$env:TOKENGATE_RATE_LIMIT = "100000"
docker compose --profile app up -d app 2>$null | Out-Null
Start-Sleep 5
Wait-App
Ok "App UP"

# 3. Teams
$suffix = Get-Random -Maximum 99999
$teamA = Invoke-RestMethod -Uri http://localhost:8080/admin/teams -Method Post -Headers $admin -ContentType "application/json" -Body ('{"name":"load-off-' + $suffix + '"}')
$teamB = Invoke-RestMethod -Uri http://localhost:8080/admin/teams -Method Post -Headers $admin -ContentType "application/json" -Body ('{"name":"load-on-' + $suffix + '"}')
Invoke-RestMethod -Uri "http://localhost:8080/admin/teams/$($teamB.id)/semantic-cache" -Method Put -Headers $admin -ContentType "application/json" -Body '{"enabled":true}' | Out-Null
Ok "Teams ready: A=$($teamA.name) (OFF), B=$($teamB.name) (ON)"

# 4-5. Runs
function Run-K6($key, $out, $label) {
    Info "Run $label chal raha hai (~2.5 min, terminal mat chhedna)..."
    Remove-Item $out -ErrorAction SilentlyContinue
    $env:TOKENGATE_KEY = $key
    k6 run --quiet --summary-export $out loadtest\semantic-loadtest.js | Out-Null
    if (-not (Test-Path $out)) { Fail "Run $label fail hua. Ye chala ke error dekho: k6 run loadtest\semantic-loadtest.js" }
    Ok "Run $label done"
}
Run-K6 $teamA.apiKey "loadtest\result-off.json" "A (semantic OFF)"
Run-K6 $teamB.apiKey "loadtest\result-on.json" "B (semantic ON)"

# 6. Logs se semantic decisions (restart se pehle!)
$logs = docker compose logs app 2>$null
$semanticHits = ($logs | Select-String "SEMANTIC HIT" | Measure-Object).Count
$guardBlocks = ($logs | Select-String "accepted=false.*unsafe" | Measure-Object).Count

# 7. Rate limit normal
Remove-Item Env:TOKENGATE_RATE_LIMIT, Env:TOKENGATE_KEY -ErrorAction SilentlyContinue
docker compose --profile app up -d app 2>$null | Out-Null
Ok "Rate limit wapas normal (10/min)"

# 8. Comparison
$A = (Get-Content "loadtest\result-off.json" -Raw | ConvertFrom-Json).metrics
$B = (Get-Content "loadtest\result-on.json" -Raw | ConvertFrom-Json).metrics
function Pct($m) { if ($m) { "{0:N1}%" -f (100 * [double]$m.value) } else { "0.0%" } }
function Cnt($m) { if ($m) { "{0:N0}" -f [double]$m.count } else { "0" } }
function Ms($m, $f) { if ($m) { "{0:N0} ms" -f [double]$m.$f } else { "-" } }

$rows = @(
    [pscustomobject]@{ Metric = "Any cache hit rate";      A = (Pct $A.any_cache_hit_rate); B = (Pct $B.any_cache_hit_rate) }
    [pscustomobject]@{ Metric = "  exact cache hits";      A = (Pct $A.exact_hit_rate);     B = (Pct $B.exact_hit_rate) }
    [pscustomobject]@{ Metric = "  semantic cache hits";   A = (Pct $A.semantic_hit_rate);  B = (Pct $B.semantic_hit_rate) }
    [pscustomobject]@{ Metric = "Tokens saved";            A = (Cnt $A.tokens_saved);       B = (Cnt $B.tokens_saved) }
    [pscustomobject]@{ Metric = "Total requests";          A = (Cnt $A.http_reqs);          B = (Cnt $B.http_reqs) }
    [pscustomobject]@{ Metric = "Latency median";          A = (Ms $A.http_req_duration "med"); B = (Ms $B.http_req_duration "med") }
    [pscustomobject]@{ Metric = "Latency p95";             A = (Ms $A.http_req_duration "p(95)"); B = (Ms $B.http_req_duration "p(95)") }
    [pscustomobject]@{ Metric = "Success (status 200)";    A = (Pct $A.checks);             B = (Pct $B.checks) }
)

Write-Host "`n=== RESULTS ===" -ForegroundColor Cyan
$rows | Format-Table -AutoSize
Write-Host "Semantic HITs in logs: $semanticHits   |   Guard blocks (safe vs unsafe): $guardBlocks`n"

$md = @("# Semantic cache load test", "", "Date: $(Get-Date -Format 'yyyy-MM-dd HH:mm')", "Traffic: k6, up to 50 virtual users, paraphrased questions, mock provider, embeddings via local Ollama (CPU)", "", "| Metric | A: semantic OFF | B: semantic ON |", "|---|---|---|")
foreach ($r in $rows) { $md += "| $($r.Metric.Trim()) | $($r.A) | $($r.B) |" }
$md += "", "Semantic HITs (logs): $semanticHits. Guard blocks (safe vs unsafe): $guardBlocks."
Set-Content -Path "loadtest\results.md" -Value $md -Encoding UTF8
Ok "Saved: loadtest\results.md"