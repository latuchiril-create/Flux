@echo off
echo Killing Java processes...
taskkill /F /IM java.exe >nul 2>&1
taskkill /F /IM javaw.exe >nul 2>&1
timeout /t 3 /nobreak >nul

echo Starting build...
cd /d d:\FugaClient
call gradlew.bat build

echo.
if %ERRORLEVEL% EQU 0 (
    echo [SUCCESS] Build completed!
    echo JAR location: d:\FugaClient\build\libs\fluxvisuals-1.0.0.jar
) else (
    echo [FAILED] Build failed with error code %ERRORLEVEL%
)
pause
