@echo off
rem Runs install-server.ps1 for this one process only (no system-wide policy change).
cd /d "%~dp0"
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0install-server.ps1" %*
pause
