import os
import sys
import subprocess
import zipfile
import time

ADB = r"D:\UserProfile\AppData\Local\Android\Sdk\platform-tools\adb.exe"
DEVICE = "192.168.1.97:34879"

def run_adb(cmd):
    return subprocess.run([ADB, "-s", DEVICE] + cmd, capture_output=True, text=True, encoding="utf-8", errors="replace")

def test_full_pipeline():
    print("[*] Connecting to ADB device...")
    subprocess.run([ADB, "connect", DEVICE], capture_output=True)

    # Launch App Cloner Studio on device to make sure it is ready
    print("[*] Starting App Cloner Studio on device...")
    run_adb(["shell", "am", "start", "-n", "com.cloner.app/com.cloner.app.activity.MainActivity"])

if __name__ == "__main__":
    test_full_pipeline()
