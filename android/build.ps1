# 构建轻聊 APK
#   - 版本号自动 +1（写回 version.properties），保证覆盖安装不被判成降级
#   - 产物名带版本号与时间戳，每次都不一样，避免下载/转发时被浏览器加 (1) 后缀
#   - 签名固定用 android/LightChat/debug.keystore（不要删、不要换，换了就必须卸载重装）
param(
    [switch]$NoBump,
    [switch]$Quiet
)

$ErrorActionPreference = "Stop"
$root = $PSScriptRoot
$propsPath = Join-Path $root "version.properties"

# ---- 读版本 ----
$code = 1
$name = "1.0"
if (Test-Path $propsPath) {
    foreach ($line in Get-Content $propsPath) {
        if ($line -match '^\s*VERSION_CODE\s*=\s*(\d+)') { $code = [int]$Matches[1] }
        elseif ($line -match '^\s*VERSION_NAME\s*=\s*([^\s#]+)') { $name = $Matches[1] }
    }
}

if (-not $NoBump) {
    $code = $code + 1
    [System.IO.File]::WriteAllLines(
        $propsPath,
        [string[]]@("VERSION_CODE=$code", "VERSION_NAME=$name"),
        (New-Object System.Text.UTF8Encoding($false))
    )
}

# ---- 签名文件检查 ----
$keystore = Join-Path $root "LightChat\debug.keystore"
if (-not (Test-Path $keystore)) {
    throw "找不到签名文件 $keystore。没有它打出来的包签名会变，手机就必须卸载重装。"
}

# ---- 构建 ----
$env:JAVA_HOME = "D:\Program Files\Huawei\DevEco Studio\jbr"
$env:ANDROID_HOME = Join-Path $root "_toolchain\sdk"
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
$env:ANDROID_USER_HOME = Join-Path $root "_toolchain\android-home"
$env:GRADLE_USER_HOME = Join-Path $root "_toolchain\gradle-home"
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"

Push-Location (Join-Path $root "LightChat")
try {
    $log = & (Join-Path $root "_toolchain\gradle\bin\gradle.bat") --no-daemon :app:assembleDebug 2>&1
    $exit = $LASTEXITCODE
} finally {
    Pop-Location
}

if ($exit -ne 0) {
    $log | Select-String -Pattern "^e: |error:|FAILED" | Select-Object -First 30 | ForEach-Object { $_.Line }
    throw "构建失败（退出码 $exit）"
}
if (-not $Quiet) { "构建成功（$name / versionCode $code）" }

# ---- 产出到 dist，文件名唯一 ----
$apk = Join-Path $root "LightChat\app\build\outputs\apk\debug\app-debug.apk"
if (-not (Test-Path $apk)) { throw "没找到构建产物 $apk" }

$stamp = Get-Date -Format "yyyyMMdd-HHmm"
$dist = Join-Path $root "dist"
New-Item -ItemType Directory -Force -Path $dist | Out-Null
$out = Join-Path $dist ("LightChat-v{0}-{1}-{2}.apk" -f $name, $code, $stamp)
Copy-Item $apk $out -Force

# 只保留最近 5 个包，避免 dist 越堆越多（都是本脚本生成的构建产物）
Get-ChildItem $dist -Filter "LightChat-v*.apk" -File |
    Sort-Object LastWriteTime -Descending |
    Select-Object -Skip 5 |
    ForEach-Object { Remove-Item $_.FullName -Force }

$size = (Get-Item $out).Length
if (-not $Quiet) {
    "✔ {0}  ({1:N0} 字节)" -f $out, $size
    "  签名证书 SHA-256: 5750608ec066ed0ab244cfae590dec0dfccb23fc9d13e08cbe7217d577e67829（固定）"
}

return $out
