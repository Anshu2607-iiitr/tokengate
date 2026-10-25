# TokenGate ko ek command me chalao:
#   powershell -ExecutionPolicy Bypass -File scripts\start.ps1
#   powershell -ExecutionPolicy Bypass -File scripts\start.ps1 -Tunnel   (internet link bhi)
param([switch]$Tunnel)
Set-Location (Split-Path $PSScriptRoot -Parent)

function Ok($msg)   { Write-Host "[OK]   $msg" -ForegroundColor Green }
function Info($msg) { Write-Host "[..]   $msg" -ForegroundColor Yellow }
function Warn($msg) { Write-Host "[!!]   $msg" -ForegroundColor Red }

Write-Host "`n=== TokenGate start ===`n" -ForegroundColor Cyan

# 1. Docker
docker info *> $null
if ($LASTEXITCODE -ne 0) {
    Info "Docker Desktop start kar raha hoon..."
    Start-Process "C:\Program Files\Docker\Docker\Docker Desktop.exe" -ErrorAction SilentlyContinue
    for ($i = 0; $i -lt 60; $i++) { Start-Sleep 3; docker info *> $null; if ($LASTEXITCODE -eq 0) { break } }
    if ($LASTEXITCODE -ne 0) { Warn "Docker start nahi hua. Docker Desktop khud kholo aur dobara chalao."; exit 1 }
}
Ok "Docker chal raha hai"

# 2. Ollama (real AI ke liye, optional)
try { Invoke-WebRequest http://localhost:11434/api/tags -UseBasicParsing -TimeoutSec 3 | Out-Null; Ok "Ollama chal raha hai" }
catch {
    Info "Ollama start kar raha hoon..."
    Start-Process "$env:LOCALAPPDATA\Programs\Ollama\ollama app.exe" -ErrorAction SilentlyContinue
    Start-Sleep 5
    try { Invoke-WebRequest http://localhost:11434/api/tags -UseBasicParsing -TimeoutSec 3 | Out-Null; Ok "Ollama chal raha hai" }
    catch { Warn "Ollama nahi mila. Real AI ki jagah mock jawab aayenge (fallback)." }
}

# 3. Admin token: .env file me hamesha ke liye (.env git me commit nahi hoti)
$envFile = ".env"
$token = $null
if (Test-Path $envFile) {
    $line = Select-String -Path $envFile -Pattern "^TOKENGATE_ADMIN_TOKEN=(.+)$" | Select-Object -First 1
    if ($line) { $token = $line.Matches[0].Groups[1].Value.Trim() }
}
if (-not $token) {
    $running = (docker compose exec -T app printenv TOKENGATE_ADMIN_TOKEN 2>$null)
    if ($running -and $running.Trim() -ne "dev-admin-secret") { $token = $running.Trim() }
    else { $token = -join ((48..57) + (97..122) | Get-Random -Count 32 | ForEach-Object { [char]$_ }) }
    Add-Content -Path $envFile -Value "TOKENGATE_ADMIN_TOKEN=$token"
    Ok "Admin token .env me save kiya"
}
Remove-Item Env:TOKENGATE_ADMIN_TOKEN -ErrorAction SilentlyContinue  # .env wala hi use ho

# 4. Saari services
Info "Services start ho rahi hain..."
docker compose --profile app up -d | Out-Null
Info "App ready hone ka wait (max 2 min)..."
$up = $false
for ($i = 0; $i -lt 60; $i++) {
    try { if ((Invoke-RestMethod http://localhost:8080/actuator/health -TimeoutSec 3).status -eq "UP") { $up = $true; break } } catch { }
    Start-Sleep 2
}
if (-not $up) { Warn "App UP nahi hui. Dekho: docker compose logs app --tail 50"; exit 1 }
Ok "TokenGate UP hai"

Write-Host "`n=== Sab ready! ===" -ForegroundColor Cyan
Write-Host "Dashboard : http://localhost:3000"
Write-Host "Swagger   : http://localhost:8080/swagger-ui.html"
Write-Host "Grafana   : http://localhost:3001"
Write-Host "Admin token: $token" -ForegroundColor Yellow
Write-Host "Band karna : powershell -ExecutionPolicy Bypass -File scripts\stop.ps1`n"

# 5. Optional: internet link
if ($Tunnel) {
    Info "Internet link bana raha hoon (ye window khuli rakho, Ctrl+C se band)..."
    cloudflared tunnel --protocol http2 --url http://localhost:3000 2>&1 | Select-String "trycloudflare"
}