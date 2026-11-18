"""装 Android SDK：Java 的 sdkmanager 连不上 dl.google.com，改用 Python 直接下官方 zip 解包。

Gradle 用你放在 D:\\Downloads 的那份 gradle-8.9-bin.zip。
"""
import os
import ssl
import sys
import time
import zipfile
import shutil
import urllib.request
import pathlib

ROOT = pathlib.Path(__file__).resolve().parent
TC = ROOT / "_toolchain"
SDK = TC / "sdk"
LOG = TC / "sdk.log"
GRADLE_ZIP_SRC = pathlib.Path(r"D:\Downloads\gradle-8.9-bin.zip")

REPO = "https://dl.google.com/android/repository/"
PKGS = [
    ("platform-tools", "platform-tools_r37.0.3-win.zip", "."),
    ("platforms;android-35", "platform-35_r02.zip", "platforms/android-35"),
    ("build-tools;35.0.0", "build-tools_r35_windows.zip", "build-tools/35.0.0"),
]
CTX = ssl.create_default_context()


def log(msg):
    line = f"[{time.strftime('%H:%M:%S')}] {msg}"
    print(line, flush=True)
    with LOG.open("a", encoding="utf-8") as f:
        f.write(line + "\n")


def download(url, dest: pathlib.Path):
    if dest.exists() and dest.stat().st_size > 1_000_000:
        log(f"skip {dest.name}")
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
            if pct >= last + 20:
                last = pct
                log(f"  {dest.name} {pct}%")
    tmp.replace(dest)


def unzip_into(src: pathlib.Path, dest: pathlib.Path):
    dest.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(src) as z:
        names = [n for n in z.namelist() if n.strip("/")]
        tops = {n.split("/")[0] for n in names}
        strip = 1 if len(tops) == 1 and all("/" in n for n in names) else 0
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
                shutil.copyfileobj(s, d)


def main():
    TC.mkdir(parents=True, exist_ok=True)

    # Gradle：用用户已经下好的那份
    gdest = TC / "gradle.zip"
    if not gdest.exists():
        if not GRADLE_ZIP_SRC.exists():
            raise RuntimeError(f"没找到 {GRADLE_ZIP_SRC}")
        shutil.copy2(GRADLE_ZIP_SRC, gdest)
        log(f"copied gradle zip {gdest.stat().st_size // 1048576}MB")
    if not (TC / "gradle" / "bin" / "gradle.bat").exists():
        unzip_into(gdest, TC / "gradle")
        log("gradle unzipped")

    # SDK
    for name, file, rel in PKGS:
        dest = TC / "cache" / file
        dest.parent.mkdir(parents=True, exist_ok=True)
        download(REPO + file, dest)
        target = SDK / rel if rel != "." else SDK
        if name.startswith("platforms;") and (SDK / "platforms" / "android-35" / "android.jar").exists():
            log(f"skip {name}")
            continue
        if name.startswith("build-tools") and (SDK / "build-tools" / "35.0.0" / "aapt2.exe").exists():
            log(f"skip {name}")
            continue
        if name == "platform-tools" and (SDK / "platform-tools" / "adb.exe").exists():
            log(f"skip {name}")
            continue
        unzip_into(dest, target)
        log(f"installed {name} -> {target}")

    checks = {
        "android.jar": SDK / "platforms" / "android-35" / "android.jar",
        "aapt2": SDK / "build-tools" / "35.0.0" / "aapt2.exe",
        "d8": SDK / "build-tools" / "35.0.0" / "d8.bat",
        "gradle": TC / "gradle" / "bin" / "gradle.bat",
    }
    ok = True
    for k, p in checks.items():
        exists = p.exists()
        ok = ok and exists
        log(f"check {k}: {'OK' if exists else 'MISSING ' + str(p)}")

    if ok:
        (TC / "READY").write_text("ok", encoding="utf-8")
        log("ALL READY")
    else:
        log("INCOMPLETE")
        sys.exit(1)


if __name__ == "__main__":
    try:
        main()
    except Exception as exc:
        log(f"FAILED: {exc!r}")
        sys.exit(1)
