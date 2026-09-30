# Updates the server files from a newer Pale Meridian server package, keeping a rollback copy.
# 1. Stop the server.  2. Back up the world (backup-world.bat).
# 3. Unzip the NEW package into a separate folder, then run:
#      powershell -NoProfile -ExecutionPolicy Bypass -File update-server.ps1 -NewPackage C:\path\to\new-package
# The world, server.properties, whitelist, ops and eula.txt are never replaced.
param([Parameter(Mandatory = $true)][string]$NewPackage, [switch]$WithTools)
$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath $PSScriptRoot
if (-not (Test-Path -LiteralPath (Join-Path $NewPackage 'server-files.tsv'))) { Write-Host "$NewPackage does not look like a Pale Meridian server package."; exit 1 }
if (Test-Path -LiteralPath '.server-running') { Write-Host 'Stop the server first.'; exit 1 }
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
New-Item -ItemType Directory -Force -Path 'backups' | Out-Null
Copy-Item -Recurse -LiteralPath 'mods' -Destination "backups\mods-$stamp"
Write-Host "Rollback copy of mods\: backups\mods-$stamp"
Get-ChildItem -Path 'mods\*.jar' | Remove-Item -Force
foreach ($f in 'server-files.tsv', 'lock.json', 'install-server.sh', 'start-server.sh', 'backup-world.sh', 'update-server.sh', 'install-server.ps1', 'install-server.bat',
         'start-server.bat', 'backup-world.ps1', 'backup-world.bat', 'update-server.ps1', 'server.properties.template', 'SERVER_GUIDE.md', 'README-FIRST.txt') {
    $src = Join-Path $NewPackage $f
    if (Test-Path -LiteralPath $src) { Copy-Item -LiteralPath $src -Destination . -Force }
}
Copy-Item -Path (Join-Path $NewPackage 'mods\palemeridian-*.jar') -Destination 'mods' -Force
if ($WithTools) { & "$PSScriptRoot\install-server.ps1" -WithTools } else { & "$PSScriptRoot\install-server.ps1" }
Write-Host "Updated. To roll back: stop the server, replace mods\ with backups\mods-$stamp, and restore the world backup if needed."
