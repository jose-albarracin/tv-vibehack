# Vuelve a poner el inicio de Google TV y quita el launcher propio (com.gathin.tvhome).
# No toca las 26 apps desactivadas (para eso: tools\restore.ps1 con config\debloat-tcl-g09.txt).
#
# Uso (PowerShell, desde C:\Apps\tv-vibehack):
#   tools\restaurar-inicio-google.ps1               # usa la IP 192.168.128.6
#   tools\restaurar-inicio-google.ps1 -TvHost <ip>
#   tools\restaurar-inicio-google.ps1 -Desinstalar  # ademas desinstala el launcher propio
param(
    [string]$TvHost = $(if ($env:TV_HOST) { $env:TV_HOST } else { '192.168.128.6' }),
    [switch]$Desinstalar
)
$ErrorActionPreference = 'Stop'
$OutputEncoding = New-Object System.Text.UTF8Encoding $false
$repo = Split-Path -Parent $PSScriptRoot
$py = Join-Path $repo 'tools\venv\Scripts\python.exe'
$env:TV_HOST = $TvHost
$cmds = @(
    "settings put secure enabled_accessibility_services ''",
    'pm enable com.google.android.tungsten.setupwraith',
    'pm enable com.google.android.apps.tv.launcherx',
    'cmd package set-home-activity --user 0 com.google.android.apps.tv.launcherx/.home.HomeActivity'
)
if ($Desinstalar) { $cmds += 'pm uninstall com.gathin.tvhome' }
$cmds += 'cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME'
$cmds += 'input keyevent KEYCODE_HOME'
$cmds | & $py -I (Join-Path $repo 'tools\tv.py') --batch
Write-Host 'Listo: el inicio de Google TV quedo como pantalla de inicio.'
