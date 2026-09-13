@echo off
echo ========================================================
echo   Flux Client: Local Build and Auto-Push to GitHub
echo ========================================================

cd /d d:\FugaClient

echo [1/4] Stopping Java processes...
taskkill /F /IM java.exe >nul 2>&1
taskkill /F /IM javaw.exe >nul 2>&1

echo [2/4] Building with Gradle...
call gradlew.bat build buildFree -x test
if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] Build failed
    pause
    exit /b 1
)

echo [3/4] Updating client jars for Loader and Minecraft...
copy /y "build\libs\fluxvisuals-licensed-1.0.0.jar" "fluxvisuals-1.0.0.jar" >nul
if exist "%APPDATA%\.minecraft\mods" copy /y "build\libs\fluxvisuals-licensed-1.0.0.jar" "%APPDATA%\.minecraft\mods\fluxvisuals-1.0.0.jar" >nul
if exist "%APPDATA%\.tlauncher\legacy\Minecraft\game\mods" copy /y "build\libs\fluxvisuals-licensed-1.0.0.jar" "%APPDATA%\.tlauncher\legacy\Minecraft\game\mods\fluxvisuals-1.0.0.jar" >nul
if exist "d:\FugaClient\game\mods" copy /y "build\libs\fluxvisuals-free-1.0.0.jar" "d:\FugaClient\game\mods\fluxvisuals-free-1.0.0.jar" >nul

if exist "E:\Mc\mods" (
    copy /y "build\libs\fluxvisuals-licensed-1.0.0.jar" "E:\Mc\mods\fluxvisuals-1.0.0.jar" >nul
    if exist "E:\Loader\scripts\encrypt-core.js" (
        pushd E:\Loader
        node scripts\encrypt-core.js >nul 2>&1
        if exist "data\flux-core.dat" copy /y "data\flux-core.dat" "assets\flux-core.dat" >nul 2>&1
        if exist "%APPDATA%\FluxClient\data\flux-core.dat" copy /y "data\flux-core.dat" "%APPDATA%\FluxClient\data\flux-core.dat" >nul 2>&1
        popd
    )
)

echo [4/4] Pushing to GitHub (triggers GitHub Actions cloud build)...
D:\Git\cmd\git.exe add .
D:\Git\cmd\git.exe commit -m "Auto-build: update client and trigger GitHub Actions build"
D:\Git\cmd\git.exe push origin main

echo.
echo ========================================================
echo [SUCCESS] Mod built locally and pushed to GitHub!
echo GitHub Actions cloud build triggered:
echo https://github.com/latuchiril-create/Flux/actions
echo ========================================================
pause


