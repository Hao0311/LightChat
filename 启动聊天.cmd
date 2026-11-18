@echo off
chcp 65001 >nul
title 本机轻聊
cd /d "%~dp0"
start "" http://127.0.0.1:8770
python "%~dp0server.py"
pause
