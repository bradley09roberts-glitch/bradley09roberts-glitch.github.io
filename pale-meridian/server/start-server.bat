@echo off
setlocal
cd /d "%~dp0"
set "LAUNCHER="
for %%F in (fabric-server-mc.*-launcher.*.jar) do set "LAUNCHER=%%F"
if not defined LAUNCHER (
  echo The Fabric server launcher is missing. Run install-server.bat first.
  pause
  exit /b 1
)
findstr /r /i /c:"^eula=true" eula.txt >nul 2>&1
if errorlevel 1 (
  echo The Minecraft EULA has not been accepted in eula.txt.
  echo Read https://aka.ms/MinecraftEULA and, if you agree, set eula=true in eula.txt yourself.
  pause
  exit /b 1
)
if not defined PM_MIN_RAM set "PM_MIN_RAM=2G"
if not defined PM_MAX_RAM set "PM_MAX_RAM=4G"
echo running> .server-running
java -Xms%PM_MIN_RAM% -Xmx%PM_MAX_RAM% -jar "%LAUNCHER%" nogui
del .server-running >nul 2>&1
pause
