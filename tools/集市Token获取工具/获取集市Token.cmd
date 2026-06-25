@echo off
chcp 65001 >nul
title Market Token Catcher

REM ====================================================================
REM  IMPORTANT: keep this .cmd file PURE ASCII.
REM  cmd.exe under chcp 65001 (UTF-8) corrupts multibyte (Chinese) bytes
REM  and breaks command parsing -> the elevation line fails -> window
REM  closes with no UAC prompt. All Chinese UI text lives in run.ps1.
REM ====================================================================

REM ---- elevate to admin if not already (needed to install the CA cert) ----
net session >nul 2>&1
if %errorlevel% NEQ 0 (
    echo Requesting administrator privileges...
    cd /d "%~dp0"
    powershell -NoProfile -Command "Start-Process cmd -ArgumentList '/c','cd','/d','%CD%','&&','%~nx0' -Verb RunAs"
    exit /b
)

cd /d "%~dp0"

REM ---- run main logic via PowerShell (UTF-8 console, bypass exec policy) ----
powershell -NoProfile -ExecutionPolicy Bypass -Command "[Console]::OutputEncoding=[Text.Encoding]::UTF8; & '.\run.ps1'"

echo.
echo Press any key to close...
pause >nul

REM ---- safety net: if a crash left proxy stuck at 127.0.0.1:8080, disable it ----
powershell -NoProfile -ExecutionPolicy Bypass -Command "try{ $r='HKCU:\Software\Microsoft\Windows\CurrentVersion\Internet Settings'; if((Get-ItemProperty $r).ProxyServer -match '127.0.0.1:8080'){ Set-ItemProperty -Path $r -Name ProxyEnable -Value 0 } }catch{}" >nul 2>&1

exit /b
