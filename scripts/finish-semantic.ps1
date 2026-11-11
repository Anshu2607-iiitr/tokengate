# Semantic cache feature ko "done" karo: tests, smoke test, safe commit.
Set-Location (Split-Path $PSScriptRoot -Parent)
function Ok($m)   { Write-Host "[OK] $m" -ForegroundColor Green }
function Info($m) { Write-Host "[..] $m" -ForegroundColor Yellow }
function Fail($m) { Write-Host "[!!] $m" -ForegroundColor Red; exit 1 }

Write-Host "`n=== Semantic cache: finish ===`n" -ForegroundColor Cyan

# 1. Tests
Info "Saare tests chal rahe hain (Docker chahiye, 2-4 min)..."
$tests = "PricingServiceTest,BudgetServiceTest,ProviderRouterTest,SystemPromptsTest,PromptNormalizerTest,SemanticGuardsTest,TokenGateIntegrationTest,StreamingIntegrationTest,RateLimitIntegrationTest,DeadLetterIntegrationTest"
& .\mvnw.cmd -q test "-Dtest=$tests"
if ($LASTEXITCODE -ne 0) { Fail "Tests fail hue. Upar ka [ERROR] aur 'Caused by' dekho." }
Ok "Saare tests pass"

# 2. Smoke test
Info "Smoke test (chalti app pe)..."
$token = (Select-String -Path .env -Pattern "^TOKENGATE_ADMIN_TOKEN=(.+)$").Matches[0].Groups[1].Value.Trim()
& powershell -ExecutionPolicy Bypass -File scripts\smoke-test.ps1 -AdminToken $token
if ($LASTEXITCODE -ne 0) { Fail "Smoke test fail. Kya app chal rahi hai? (scripts\start.ps1)" }
Ok "Smoke test pass"

# 3. Secrets kabhi commit na hon
$gi = Get-Content .gitignore -ErrorAction SilentlyContinue
foreach ($p in @(".env", "backup.sql", "loadtest/result-*.json")) {
    if ($gi -notcontains $p) { Add-Content .gitignore $p }
}
Ok ".gitignore updated"

# 4. CI me naye unit tests
$ci = ".github\workflows\ci.yml"
if (Test-Path $ci) {
    $c = [IO.File]::ReadAllText("$PWD\$ci")
    if ($c -notmatch "SemanticGuardsTest") {
        $c = $c.Replace("ProviderRouterTest,", "ProviderRouterTest,PromptNormalizerTest,SemanticGuardsTest,")
        [IO.File]::WriteAllText("$PWD\$ci", $c)
    }
    Ok "CI test list updated"
}

# 5. Sirf sahi files stage
git add src scripts experiments loadtest docs README.md .gitignore docker-compose.yml docker-compose.prod.yml .github 2>$null

# 6. Safety check
$staged = git diff --cached --name-only
if ($staged | Where-Object { $_ -match '(^|/)\.env$' -or $_ -match 'backup\.sql' -or $_ -match 'result-.*\.json' }) {
    git reset -q
    Fail "Ek secret/data file stage ho rahi thi. Sab unstage kar diya, kuch commit nahi hua."
}
if (-not $staged) { Ok "Commit karne ko kuch naya nahi"; exit 0 }

Write-Host "`nCommit hone wali files:" -ForegroundColor Cyan
$staged | ForEach-Object { "  $_" }

# 7. Commit
git commit -q -m "Semantic cache: prompt normalization, embeddings + pgvector (Flyway V5), negation/number guards, per-team opt-in, tokens-saved metric, k6 before/after load test, docs"
Ok "Commit ho gaya"
Write-Host "`nGitHub pe bhejne ke liye:  git push`n" -ForegroundColor Cyan