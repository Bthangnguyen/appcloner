import subprocess
import sys
import time

ADB = r"D:\UserProfile\AppData\Local\Android\Sdk\platform-tools\adb.exe"
DEVICE = "192.168.1.97:34879"

def adb_cmd(args):
    cmd = [ADB, "-s", DEVICE] + args
    return subprocess.run(cmd, capture_output=True, text=True, encoding="utf-8", errors="replace")

def ensure_connected():
    c = subprocess.run([ADB, "connect", DEVICE], capture_output=True, text=True)
    time.sleep(0.5)
    return c.stdout

if __name__ == "__main__":
    ensure_connected()
    
    # 1. Device Info
    model = adb_cmd(["shell", "getprop", "ro.product.model"]).stdout.strip()
    android_ver = adb_cmd(["shell", "getprop", "ro.build.version.release"]).stdout.strip()
    sdk_ver = adb_cmd(["shell", "getprop", "ro.build.version.sdk"]).stdout.strip()
    print(f"[*] DEVICE CONNECTED: {model} (Android {android_ver}, API {sdk_ver})")
    
    # 2. Find cloned and original packages
    pkgs = adb_cmd(["shell", "pm", "list", "packages"]).stdout
    relevant = [p.replace("package:", "").strip() for p in pkgs.split("\n") if any(k in p.lower() for k in ["cloner", "device", "info", "hw", "tiktok", "flir"])]
    print(f"\n[*] Relevant Installed Packages ({len(relevant)}):")
    for r in relevant:
        print(f"    - {r}")
