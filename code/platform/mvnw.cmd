@REM ----------------------------------------------------------------------------
@REM Maven Wrapper Script for W6W Platform
@REM ----------------------------------------------------------------------------
@echo off
setlocal

set "DIR=%~dp0"
if exist "%DIR%\.maven\apache-maven-3.9.9\bin\mvn.cmd" (
    "%DIR%\.maven\apache-maven-3.9.9\bin\mvn.cmd" %*
    exit /b %ERRORLEVEL%
)

where mvn >nul 2>nul
if %ERRORLEVEL% equ 0 (
    mvn %*
    exit /b %ERRORLEVEL%
)

echo ERROR: Maven not found.
exit /b 1
