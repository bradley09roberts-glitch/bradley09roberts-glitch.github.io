# Pale Meridian - dedicated server installer (Windows PowerShell 5.1 or newer).
#
# Downloads the pinned Fabric server launcher and server-side mods listed in server-files.tsv,
# verifies every file's hash, and prepares this folder as a server. Safe to re-run: it never touches
# world\, server.properties, whitelist.json, ops.json or an existing eula.txt.
#
# It does NOT accept the Minecraft EULA for you: eula.txt is created with eula=false and you must
# read https://aka.ms/MinecraftEULA and change it yourself before the server will start.
#
# Run it by double-clicking install-server.bat, or:  powershell -NoProfile -ExecutionPolicy Bypass -File install-server.ps1 [-WithTools]
param([switch]$WithTools)
$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath $PSScriptRoot
try { [Net.ServicePointManager]::SecurityProtocol = [Net.ServicePointManager]::SecurityProtocol -bor [Net.SecurityProtocolType]::Tls12 } catch { }

function Fail($msg) { Write-Host "ERROR: $msg" -ForegroundColor Red; exit 1 }

# Java 25 or newer (java -version prints to stderr; cmd /c keeps PowerShell from treating that as an error)
# take the line with 'version "': JAVA_TOOL_OPTIONS and similar make Java print extra lines first
$javaLine = (cmd /c "java -version 2>&1" | Where-Object { $_ -match 'version "' } | Select-Object -First 1)
if (-not $javaLine) { Fail "Java was not found. Install a Java 25 runtime (for example Eclipse Temurin 25) and try again." }
if ("$javaLine" -match 'version "(\d+)') { $major = [int]$Matches[1] } else { $major = 0 }
if ($major -lt 25) { Fail "Java 25 or newer is required (found: $javaLine)." }

$ua = 'PaleMeridian-installer/1.0'
function Get-Hash($path, $algo) { (Get-FileHash -Algorithm $algo.ToUpper() -LiteralPath $path).Hash.ToLowerInvariant() }

function Fetch($url, $dest, $algo, $want) {
    if (Test-Path -LiteralPath $dest) {
        if ((Get-Hash $dest $algo) -eq $want) { Write-Host "  ok (already present) $(Split-Path $dest -Leaf)"; return }
        Write-Host "  replacing $(Split-Path $dest -Leaf) (hash mismatch)"
    }
    $tmp = "$dest.part"
    $ok = $false
    for ($i = 1; $i -le 3 -and -not $ok; $i++) {
        try { Invoke-WebRequest -Uri $url -OutFile $tmp -UseBasicParsing -UserAgent $ua; $ok = $true }
        catch { Write-Host "  download failed (attempt $i), retrying..."; Start-Sleep -Seconds (2 * $i) }
    }
    if (-not $ok) { Remove-Item -LiteralPath $tmp -ErrorAction SilentlyContinue; Fail "could not download $url" }
    $got = Get-Hash $tmp $algo
    if ($got -ne $want) {
        Remove-Item -LiteralPath $tmp -ErrorAction SilentlyContinue
        Fail "hash mismatch for $url`n       expected $algo $want`n       got          $got`n       Nothing was installed for this file. Do not bypass this check."
    }
    Move-Item -LiteralPath $tmp -Destination $dest -Force
    Write-Host "  verified $(Split-Path $dest -Leaf)"
}

New-Item -ItemType Directory -Force -Path 'mods' | Out-Null
Write-Host 'Pale Meridian server install'
foreach ($line in Get-Content -LiteralPath 'server-files.tsv') {
    if ($line -match '^\s*(#|$)') { continue }
    $p = $line -split "`t"
    $kind, $name, $url, $algo, $hash = $p[0], $p[1], $p[2], $p[3], $p[4]
    switch ($kind) {
        'launcher' { Fetch $url $name $algo $hash }
        'mod'      { Fetch $url (Join-Path 'mods' $name) $algo $hash }
        'tool'     { if ($WithTools) { Fetch $url (Join-Path 'mods' $name) $algo $hash } }
    }
}
if (-not (Test-Path -LiteralPath 'mods\palemeridian-1.0.0.jar')) { Fail 'mods\palemeridian-1.0.0.jar is missing from the package.' }
if (-not (Test-Path -LiteralPath 'server.properties')) { Copy-Item 'server.properties.template' 'server.properties'; Write-Host '  created server.properties (private: whitelist on)' }
if (-not (Test-Path -LiteralPath 'eula.txt')) {
    Set-Content -LiteralPath 'eula.txt' -Encoding ASCII -Value @('# Read the Minecraft EULA at https://aka.ms/MinecraftEULA', '# Change the next line to eula=true ONLY if you agree to it.', 'eula=false')
    Write-Host '  created eula.txt (eula=false)'
}
Write-Host ''
Write-Host 'Install complete. Before the first start:'
Write-Host '  1. Read the Minecraft EULA: https://aka.ms/MinecraftEULA'
Write-Host '     If you agree, open eula.txt in Notepad and change eula=false to eula=true yourself.'
Write-Host '  2. Start the server with start-server.bat, then add your players in its console:'
Write-Host '       whitelist add <PlayerName>'
Write-Host '  3. Players connect with the Pale Meridian client pack (same version).'
Write-Host 'See SERVER_GUIDE.md for ports, backups, updates and troubleshooting.'
