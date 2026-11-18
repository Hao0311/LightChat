@echo off
chcp 65001 >nul
cd /d "%~dp0"
set "ADB=%~dp0_toolchain\sdk\platform-tools\adb.exe"

rem 只装 dist 里最新的那个版本化 APK
set "TARGET="
for /f "delims=" %%f in ('dir /b /o-d "%~dp0dist\LightChat-v*.apk" 2^>nul') do if not defined TARGET set "TARGET=%~dp0dist\%%f"

if not defined TARGET (
  echo 还没有 APK，先双击 build.cmd 打一个包。
  pause
  exit /b 1
)

"%ADB%" devices
echo.
echo 要安装：%TARGET%
echo 手机用 USB 连上电脑、打开「开发者选项 → USB 调试」，然后按任意键继续装。
pause >nul

for /f "skip=1 tokens=1,2" %%a in ('"%ADB%" devices') do (
  if "%%b"=="device" (
    echo 找到设备 %%a，正在安装 -r（保留数据、覆盖升级）……
    "%ADB%" install -r "%TARGET%"
    goto :done
  )
)
echo 没检测到设备。检查线、USB 调试开关，或者直接把这个 APK 拷到手机里手动装。
goto :end

:done
echo.
echo 装好了，手机上找「轻聊」。
:end
pause
