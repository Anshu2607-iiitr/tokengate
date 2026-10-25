# TokenGate band karo (data safe rehta hai):
#   powershell -ExecutionPolicy Bypass -File scripts\stop.ps1
Set-Location (Split-Path $PSScriptRoot -Parent)
docker compose --profile app stop
Write-Host "TokenGate band ho gaya. Data safe hai. Dobara: scripts\start.ps1" -ForegroundColor Green