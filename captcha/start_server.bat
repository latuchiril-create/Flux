@echo off
rem Captcha YOLO server launcher for FugaClient CaptchaSolver.
rem The solver POSTs PNG frames to http://127.0.0.1:5000/solve
rem and expects {"result": "<digits>"} back.
cd /d "%~dp0"
if not exist "best.pt" (
    if exist "best(1).pt" (
        echo Copying best(1).pt to best.pt ...
        copy /y "best(1).pt" "best.pt" >nul
    ) else (
        echo ERROR: model file not found (best.pt or best(1).pt).
        pause
        exit /b 1
    )
)
set SERVER_TXT=
for %%f in ("%~dp0*.txt") do set SERVER_TXT=%%f
if "%SERVER_TXT%"=="" (
    echo ERROR: server script (*.txt) not found in %~dp0
    pause
    exit /b 1
)
echo Starting captcha server: %SERVER_TXT%
echo Endpoint: http://127.0.0.1:5000/solve
python "%SERVER_TXT%"
pause
