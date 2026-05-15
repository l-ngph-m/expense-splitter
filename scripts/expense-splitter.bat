@echo off
cd /d "%~dp0"
"bin\java" -m expense.splitter/org.Main %*
if errorlevel 1 (
    echo.
    echo Application exited with code %errorlevel%
    pause
)
