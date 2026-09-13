@echo off
cd /d d:\FugaClient
call gradlew.bat build
if %ERRORLEVEL% EQU 0 (
    echo BUILD SUCCESS > build_result.txt
) else (
    echo BUILD FAILED > build_result.txt
)
