@echo off
cd /d "%~dp0"
where java >nul 2>nul
if errorlevel 1 (
  echo Install Java 17 or newer and make sure java is on PATH.
  pause
  exit /b 1
)
java -cp "classes;lib/*" bayt.Main
pause
