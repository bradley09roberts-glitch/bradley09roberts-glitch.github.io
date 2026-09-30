# Makes a timestamped backup of the world (and the settings that matter) into backups\.
# Stop the server first: a copy taken while it is writing can be inconsistent.
param([int]$Keep = 10)
$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath $PSScriptRoot
if (Test-Path -LiteralPath '.server-running') {
    Write-Host 'The server appears to be running (.server-running exists). Stop it first (type stop in its console).'
    Write-Host 'If it crashed, delete .server-running and try again.'
    exit 1
}
$level = 'world'
if (Test-Path -LiteralPath 'server.properties') {
    $m = Select-String -LiteralPath 'server.properties' -Pattern '^level-name=(.*)$' | Select-Object -First 1
    if ($m -and $m.Matches[0].Groups[1].Value) { $level = $m.Matches[0].Groups[1].Value }
}
if (-not (Test-Path -LiteralPath $level)) { Write-Host "No world folder '$level' yet."; exit 1 }
New-Item -ItemType Directory -Force -Path 'backups' | Out-Null
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$out = "backups\$level-$stamp.zip"
$items = @($level, 'server.properties') + @('whitelist.json', 'ops.json' | Where-Object { Test-Path -LiteralPath $_ })
Compress-Archive -Path $items -DestinationPath $out
Write-Host "Backup written: $out"
Get-ChildItem -Path "backups\$level-*.zip" | Sort-Object LastWriteTime -Descending | Select-Object -Skip $Keep | Remove-Item -Force
Write-Host "Kept the newest $Keep backups."
