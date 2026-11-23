@echo off
cd /d "%~dp0"
if not exist classes mkdir classes
setlocal enabledelayedexpansion
set BAYT_SOURCE_FILES=
for %%f in (src\bayt\*.java) do set BAYT_SOURCE_FILES=!BAYT_SOURCE_FILES! "%%f"
javac --release 17 -encoding UTF-8 -cp "lib/*" -d classes !BAYT_SOURCE_FILES!
if errorlevel 1 pause
