@echo off
chcp 65001 >nul
cd /d %~dp0
rem === FugaClient quick start ===
rem Memory in MB (3 GB like Legacy Launcher: minecraft.xmx=3072)
set MC_MEMORY_MB=3072
rem Game version (matches gradle.properties minecraft_version=1.21.8)
set MC_VERSION=Fabric 1.21.8
rem Usage: start.bat [nobuild] - skip gradle build, just launch
rem Rebuild only if free jar is missing or older than BUILD_MAX_AGE_SEC
set BUILD_MAX_AGE_SEC=120

if /i "%1"=="nobuild" goto launch

set JAR_PATH=%~dp0build\libs\fluxvisuals-free-1.0.0.jar
set JAR_AGE=-1
for /f %%a in ('powershell -NoProfile -Command "if (Test-Path '%JAR_PATH%') { [int]((Get-Date) - (Get-Item '%JAR_PATH%').LastWriteTime).TotalSeconds } else { -1 }"') do set JAR_AGE=%%a
if %JAR_AGE% GEQ 0 if %JAR_AGE% LEQ %BUILD_MAX_AGE_SEC% (
    echo [1/3] Build skipped - jar is %JAR_AGE%s old, fresher than %BUILD_MAX_AGE_SEC%s.
    goto launch
)

echo [1/3] Building mod (FREE version)...
call gradlew.bat buildFree
if %ERRORLEVEL% NEQ 0 (
    echo BUILD FAILED
    pause
    exit /b 1
)

:launch
echo [2/3] Sync settings+mods, [3/3] launch...
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0launch.ps1" -MemoryMb %MC_MEMORY_MB% -Version "%MC_VERSION%"
if %ERRORLEVEL% NEQ 0 (
    echo LAUNCH FAILED
    pause
    exit /b 1
)
exit /b 0

