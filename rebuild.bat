@echo off
chcp 65001 >nul
echo ========================================================
echo   Flux Client: Локальная сборка и публикация на GitHub
echo ========================================================

cd /d d:\FugaClient

echo [1/4] Завершение зависших процессов Java...
taskkill /F /IM java.exe >nul 2>&1
taskkill /F /IM javaw.exe >nul 2>&1

echo [2/4] Сборка мода через Gradle...
call gradlew.bat build buildFree
if %ERRORLEVEL% NEQ 0 (
    echo.
    echo [ОШИБКА] Сборка завершилась с ошибкой!
    pause
    exit /b 1
)

echo [3/4] Обновление файлов клиента для Loader и Minecraft...
copy /y build\libs\fluxvisuals-licensed-1.0.0.jar fluxvisuals-1.0.0.jar >nul
if exist "%APPDATA%\.minecraft\mods" copy /y build\libs\fluxvisuals-licensed-1.0.0.jar "%APPDATA%\.minecraft\mods\fluxvisuals-1.0.0.jar" >nul
if exist "%APPDATA%\.tlauncher\legacy\Minecraft\game\mods" copy /y build\libs\fluxvisuals-licensed-1.0.0.jar "%APPDATA%\.tlauncher\legacy\Minecraft\game\mods\fluxvisuals-1.0.0.jar" >nul
if exist "d:\FugaClient\game\mods" copy /y build\libs\fluxvisuals-free-1.0.0.jar "d:\FugaClient\game\mods\fluxvisuals-free-1.0.0.jar" >nul

if exist "E:\Mc\mods" (
    copy /y build\libs\fluxvisuals-licensed-1.0.0.jar "E:\Mc\mods\fluxvisuals-1.0.0.jar" >nul
    if exist "E:\Loader\scripts\encrypt-core.js" (
        pushd E:\Loader
        node scripts\encrypt-core.js >nul 2>&1
        if exist "data\flux-core.dat" copy /y "data\flux-core.dat" "assets\flux-core.dat" >nul 2>&1
        if exist "%APPDATA%\FluxClient\data\flux-core.dat" copy /y "data\flux-core.dat" "%APPDATA%\FluxClient\data\flux-core.dat" >nul 2>&1
        popd
    )
)

echo [4/4] Отправка на GitHub (запуск сборки в облаке GitHub Actions)...
D:\Git\cmd\git.exe add .
D:\Git\cmd\git.exe commit -m "Auto-build: обновление клиента и запуск сборки GitHub Actions"
D:\Git\cmd\git.exe push origin main

echo.
echo ========================================================
echo [УСПЕХ] Мод собран локально и отправлен на GitHub!
echo Сборка в GitHub Actions запущена автоматически:
echo https://github.com/latuchiril-create/Flux/actions
echo ========================================================
pause

