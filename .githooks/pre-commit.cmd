@echo off
setlocal
set "HOOK=%~dp0pre-commit"
if exist "%ProgramFiles%\Git\bin\bash.exe" (
  "%ProgramFiles%\Git\bin\bash.exe" "%HOOK%"
) else (
  bash "%HOOK%"
)
exit /b %ERRORLEVEL%
