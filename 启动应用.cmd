@echo off
chcp 65001 >nul
powershell -NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -File "%~dp0启动应用.ps1"
