$ErrorActionPreference = "SilentlyContinue"
Unregister-ScheduledTask -TaskName "Project Superhuman PC Companion" -Confirm:$false
Get-CimInstance Win32_Process | Where-Object { $_.CommandLine -like "*ProjectSuperhuman*pc-companion*superhuman_pc.py*" } | Invoke-CimMethod -MethodName Terminate | Out-Null
Write-Host "Startup task removed. Data remains in %LOCALAPPDATA%\ProjectSuperhuman\pc-companion."
