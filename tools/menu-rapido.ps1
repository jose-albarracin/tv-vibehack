# Menu rapido con el boton de engranaje encima de cualquier app (servicio de accesibilidad del launcher).
# Solo escucha esa tecla: no lee la pantalla ni lo que escribes.
#
# Uso (PowerShell, desde C:\Apps\tv-vibehack):
#   tools\menu-rapido.ps1 -Activar        # (tambien despues de reinstalar el launcher)
#   tools\menu-rapido.ps1 -Desactivar
#   tools\menu-rapido.ps1 -Depurar        # enciende los registros (adb logcat -s QuickMenuService)
#   tools\menu-rapido.ps1 -SinDepurar
param(
    [string]$TvHost = $(if ($env:TV_HOST) { $env:TV_HOST } else { '192.168.128.6' }),
    [switch]$Activar, [switch]$Desactivar, [switch]$Depurar, [switch]$SinDepurar
)
$ErrorActionPreference = 'Stop'
$OutputEncoding = New-Object System.Text.UTF8Encoding $false
$repo = Split-Path -Parent $PSScriptRoot
$py = Join-Path $repo 'tools\venv\Scripts\python.exe'
$env:TV_HOST = $TvHost
$svc = 'com.gathin.tvhome/com.gathin.tvhome.QuickMenuService'
$cmds = @()
if ($Activar) { $cmds += "settings put secure enabled_accessibility_services ''", "settings put secure enabled_accessibility_services $svc", 'settings put secure accessibility_enabled 1' }
if ($Desactivar) { $cmds += "settings put secure enabled_accessibility_services ''", 'settings put secure accessibility_enabled 0' }
if ($Depurar) { $cmds += 'setprop log.tag.QuickMenuService DEBUG' }
if ($SinDepurar) { $cmds += 'setprop log.tag.QuickMenuService INFO' }
if (-not $cmds) { throw 'Usa -Activar, -Desactivar, -Depurar o -SinDepurar' }
$cmds += 'settings get secure enabled_accessibility_services'
$cmds | & $py -I (Join-Path $repo 'tools\tv.py') --batch
