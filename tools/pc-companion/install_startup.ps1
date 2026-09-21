$ErrorActionPreference = "Stop"
$AppDir = Join-Path $env:LOCALAPPDATA "ProjectSuperhuman\pc-companion"
New-Item -ItemType Directory -Force -Path $AppDir | Out-Null
Copy-Item "$PSScriptRoot\superhuman_pc.py" "$AppDir\superhuman_pc.py" -Force

$python = (Get-Command pythonw.exe -ErrorAction SilentlyContinue)
if (-not $python) { throw "pythonw.exe not found. Install Python 3 and enable 'Add Python to PATH'." }

$action = New-ScheduledTaskAction -Execute $python.Source -Argument ('"' + (Join-Path $AppDir "superhuman_pc.py") + '"')
$trigger = New-ScheduledTaskTrigger -AtLogOn
$settings = New-ScheduledTaskSettingsSet -ExecutionTimeLimit ([TimeSpan]::Zero) -RestartCount 3 -RestartInterval (New-TimeSpan -Minutes 1)
Register-ScheduledTask -TaskName "Project Superhuman PC Companion" -Action $action -Trigger $trigger -Settings $settings -Description "Local Project Superhuman PC telemetry bridge" -Force | Out-Null

Write-Host "Installed. It will start automatically when you sign in."
Write-Host "Starting now..."
Start-Process -FilePath $python.Source -ArgumentList ('"' + (Join-Path $AppDir "superhuman_pc.py") + '"') -WindowStyle Hidden
Write-Host "If Windows Firewall asks, allow Private networks only."
