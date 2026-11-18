"""一次性准备 Android 构建工具链（便携版，全部放在 chat/android/_toolchain 下，不动系统环境）。

JDK 直接用 DevEco 自带的 JBR 21；其余从可访问的镜像下载。
输出写到 _toolchain/setup.log，最后的就绪标记是 _toolchain/READY。
"""
import os
import ssl
import sys
import time
import zipfile
import urllib.request
import subprocess
import pathlib

ROOT = pathlib.Path(__file__).resolve().parent
TC = ROOT / "_toolchain"
LOG = TC / "setup.log"
TC.mkdir(parents=True, exist_ok=True)

JBR = pathlib.Path(r"D:\Program Files\Huawei\DevEco Studio\jbr")
GRADLE_URL = "https://mirrors.huaweicloud.com/gradle/gradle-8.9-bin.zip"
CMDTOOLS_URL = "https://dl.google.com/android/repository/commandlinetools-win-11076708_latest.zip"

CTX = ssl.create_default_context()


def log(msg):
    line = f"[{time.strftime('%H:%M:%S')}] {msg}"
    print(line, flush=True)
    with LOG.open("a", encoding="utf-8") as f:
        f.write(line + "\n")


def download(url, dest: pathlib.Path):
    if dest.exists() and dest.stat().st_size > 1_000_000:
        log(f"skip {dest.name} ({dest.stat().st_size // 1048576}MB)")
        return
    log(f"download {url}")
    tmp = dest.with_suffix(dest.suffix + ".part")
    req = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0"})
    with urllib.request.urlopen(req, timeout=60, context=CTX) as r, open(tmp, "wb") as f:
        total = int(r.headers.get("Content-Length") or 0)
        got = 0
        last = -10
        while True:
            chunk = r.read(1 << 20)
            if not chunk:
                break
            f.write(chunk)
            got += len(chunk)
            pct = int(got * 100 / total) if total else 0
            if pct >= last + 10:
                last = pct
                log(f"  {dest.name} {pct}% ({got // 1048576}MB)")
    tmp.replace(dest)
    log(f"done {dest.name} {dest.stat().st_size // 1048576}MB")


def unzip(src: pathlib.Path, dest: pathlib.Path, strip: int = 0):
    log(f"unzip {src.name}")
    dest.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(src) as z:
        for info in z.infolist():
            parts = info.filename.split("/")[strip:]
            if not parts or not parts[-1]:
                continue
            target = dest.joinpath(*parts)
            if info.is_dir():
                target.mkdir(parents=True, exist_ok=True)
                continue
            target.parent.mkdir(parents=True, exist_ok=True)
            with z.open(info) as s, open(target, "wb") as d:
                d.write(s.read())


def main():
    if not (JBR / "bin" / "java.exe").exists():
        raise RuntimeError(f"没找到 DevEco 的 JBR：{JBR}")
    log(f"JDK = {JBR}")

    gradle_zip = TC / "gradle.zip"
    gradle_dir = TC / "gradle"
    download(GRADLE_URL, gradle_zip)
    if not (gradle_dir / "bin" / "gradle.bat").exists():
        unzip(gradle_zip, gradle_dir, strip=1)

    cmd_zip = TC / "cmdline-tools.zip"
    sdk_dir = TC / "sdk"
    latest = sdk_dir / "cmdline-tools" / "latest"
    download(CMDTOOLS_URL, cmd_zip)
    if not (latest / "bin" / "sdkmanager.bat").exists():
        unzip(cmd_zip, sdk_dir / "cmdline-tools", strip=0)
        extracted = sdk_dir / "cmdline-tools" / "cmdline-tools"
        if extracted.exists() and not latest.exists():
            extracted.rename(latest)

    env = dict(os.environ)
    env["JAVA_HOME"] = str(JBR)
    env["PATH"] = str(JBR / "bin") + os.pathsep + env.get("PATH", "")
    # dl.google.com 对 Java 默认 TLS 握手会被掐断，必须显式指定 TLS1.3
    env["JAVA_OPTS"] = "-Dhttps.protocols=TLSv1.3 -Djdk.tls.client.protocols=TLSv1.3 -Djava.net.preferIPv4Stack=true"
    # sdkmanager 默认会把缓存写到 ~/.android，沙箱下写不进去
    android_home = TC / "android-home"
    android_home.mkdir(parents=True, exist_ok=True)
    env["ANDROID_USER_HOME"] = str(android_home)

    sdkmanager = latest / "bin" / "sdkmanager.bat"
    yes = ("y\r\n" * 60)
    for pkg in ["platform-tools", "platforms;android-35", "build-tools;35.0.0"]:
        log(f"sdkmanager install {pkg}")
        p = subprocess.run([str(sdkmanager), f"--sdk_root={sdk_dir}", pkg],
                           input=yes, capture_output=True, text=True,
                           encoding="utf-8", errors="replace", env=env)
        for line in ((p.stdout or "") + (p.stderr or "")).strip().splitlines()[-6:]:
            log("  " + line)

    (TC / "READY").write_text("ok", encoding="utf-8")
    log("ALL READY")


if __name__ == "__main__":
    try:
        main()
    except Exception as exc:
        log(f"FAILED: {exc!r}")
        sys.exit(1)
