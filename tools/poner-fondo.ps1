# Manda una foto del PC a la TV y la pone de fondo del launcher.
# La foto queda tambien en la TV, en Imagenes\Fondos, para elegirla despues desde Ajustes > Mi foto.
#
# Uso (PowerShell, desde C:\Apps\tv-vibehack):
#   tools\poner-fondo.ps1 C:\ruta\foto.jpg              # la manda y la pone de fondo
#   tools\poner-fondo.ps1 C:\ruta\foto.jpg -SoloCopiar  # solo la deja en la TV
#   tools\poner-fondo.ps1 C:\ruta\foto.jpg -TvHost <ip>
param(
    [Parameter(Mandatory = $true, Position = 0)][string]$Imagen,
    [string]$TvHost = $(if ($env:TV_HOST) { $env:TV_HOST } else { '192.168.128.6' }),
    [switch]$SoloCopiar
)
$ErrorActionPreference = 'Stop'
$OutputEncoding = New-Object System.Text.UTF8Encoding $false
$repo = Split-Path -Parent $PSScriptRoot
$py = Join-Path $repo 'tools\venv\Scripts\python.exe'
$env:TV_HOST = $TvHost

$src = (Resolve-Path $Imagen).Path
$ext = [IO.Path]::GetExtension($src).ToLower()
if ($ext -notin '.jpg', '.jpeg', '.png', '.webp', '.heic', '.heif', '.bmp') { throw "Formato no soportado: $ext" }
# Nombre sin espacios ni tildes para no pelear con el shell de la TV.
$base = [IO.Path]::GetFileNameWithoutExtension($src).Normalize([Text.NormalizationForm]::FormD)
$base = ($base -replace '\p{Mn}', '') -replace '[^A-Za-z0-9_-]', '_'
$dest = "/sdcard/Pictures/Fondos/$base$ext"
$tmp = "/data/local/tmp/fondo$ext"

& $py -I (Join-Path $repo 'tools\b64push.py') $src $tmp
if ($LASTEXITCODE -ne 0) { throw 'No se pudo subir la foto a la TV' }
$cmds = @(
    'mkdir -p /sdcard/Pictures/Fondos',
    "cp $tmp $dest",
    "rm -f $tmp",
    "ls -l $dest",
    'pm grant com.gathin.tvhome android.permission.READ_EXTERNAL_STORAGE'
)
if (-not $SoloCopiar) { $cmds += "am start -n com.gathin.tvhome/.MainActivity --es fondo $dest" }
$cmds | & $py -I (Join-Path $repo 'tools\tv.py') --batch
if ($SoloCopiar) { Write-Host "Copiada en la TV: $dest (elígela en Ajustes > Mi foto)" }
else { Write-Host "Listo: $dest es el fondo del launcher." }
