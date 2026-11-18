@echo off
chcp 65001 >nul
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0build.ps1"
if errorlevel 1 (
  echo.
  echo 构建失败，看上面的报错。
  pause
  exit /b 1
)
echo.
pause
