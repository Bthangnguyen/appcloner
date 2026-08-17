import os
import sys
import subprocess
import zipfile
import time
import shutil

ADB = r"D:\UserProfile\AppData\Local\Android\Sdk\platform-tools\adb.exe"
DEVICE = "192.168.1.97:34879"

def run_adb(cmd):
    return subprocess.run([ADB, "-s", DEVICE] + cmd, capture_output=True, text=True, encoding="utf-8", errors="replace")

def test_repack():
    print("[*] Starting complete repackage test...")
    subprocess.run([ADB, "connect", DEVICE], capture_output=True)

if __name__ == "__main__":
    test_repack()
