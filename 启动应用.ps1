# 本机轻聊：以「应用窗口」方式启动（独立窗口，没有地址栏、没有标签页）
# 服务没在跑就先在后台拉起来，再开窗口。
$ErrorActionPreference = 'SilentlyContinue'
$here = Split-Path -Parent $MyInvocation.MyCommand.Path
$port = 8770
$url  = "http://127.0.0.1:$port"

function Test-Port([int]$p) {
  $client = New-Object System.Net.Sockets.TcpClient
  try {
    $iar = $client.BeginConnect('127.0.0.1', $p, $null, $null)
    if (-not $iar.AsyncWaitHandle.WaitOne(300)) { return $false }
    $client.EndConnect($iar)
    return $true
  } catch { return $false } finally { $client.Close() }
}

if (-not (Test-Port $port)) {
  Start-Process -WindowStyle Hidden -FilePath 'python' -ArgumentList 'server.py' -WorkingDirectory $here
  for ($i = 0; $i -lt 40; $i++) {
    Start-Sleep -Milliseconds 250
    if (Test-Port $port) { break }
  }
}

$candidates = @(
  "$env:ProgramFiles\Microsoft\Edge\Application\msedge.exe",
  "${env:ProgramFiles(x86)}\Microsoft\Edge\Application\msedge.exe",
  "$env:LOCALAPPDATA\Microsoft\Edge\Application\msedge.exe",
  "$env:ProgramFiles\Google\Chrome\Application\chrome.exe",
  "${env:ProgramFiles(x86)}\Google\Chrome\Application\chrome.exe",
  "$env:LOCALAPPDATA\Google\Chrome\Application\chrome.exe"
)
$exe = $candidates | Where-Object { $_ -and (Test-Path $_) } | Select-Object -First 1

if ($exe) {
  Start-Process -FilePath $exe -ArgumentList "--app=$url", "--window-size=1200,860"
} else {
  Start-Process $url
}
