import os
import sys
import subprocess
import zipfile
import time
import shutil

ADB = r"D:\UserProfile\AppData\Local\Android\Sdk\platform-tools\adb.exe"
DEVICE = "192.168.1.97:34879"
APKSIGNER = r"D:\UserProfile\AppData\Local\Android\Sdk\build-tools\34.0.0\apksigner.bat"
ZIPALIGN = r"D:\UserProfile\AppData\Local\Android\Sdk\build-tools\34.0.0\zipalign.exe"

def run_cmd(cmd):
    return subprocess.run([ADB, "-s", DEVICE] + cmd, capture_output=True, text=True, encoding="utf-8", errors="replace")

def test_full():
    print("[*] Reconnecting to ADB...")
    subprocess.run([ADB, "connect", DEVICE], capture_output=True)

    # 1. Check if user is currently cloning on phone
    log_res = run_cmd(["shell", "cat", "/storage/emulated/0/Android/data/com.cloner.app/files/clones/latest_clone.log"])
    print("[*] Current clone log on device:\n", log_res.stdout[-500:])

if __name__ == "__main__":
    test_full()
