import subprocess
import zipfile
import os
import sys

ADB = r"D:\UserProfile\AppData\Local\Android\Sdk\platform-tools\adb.exe"
DEVICE = "192.168.1.97:34879"

def run_test():
    print("[*] Rebuilding test APK for Device Info HW (Pure Java + Runtime DEX, NO unnecessary native .so)...")
    
    # 1. Inspect original APK
    orig_apk = r"d:\Workspaces\clone app\original_deviceinfo.apk"
    out_unsigned = r"d:\Workspaces\clone app\test_deviceinfo_unsigned.apk"
    out_signed = r"d:\Workspaces\clone app\test_deviceinfo_signed.apk"
    runtime_dex = r"d:\Workspaces\clone app\AppClonerProject\app\src\main\assets\runtime_classes.dex"
    
    # Extract classes from orig
    dex_classes = set()
    with zipfile.ZipFile(orig_apk, 'r') as zin:
        for entry in zin.namelist():
            if entry.endswith(".dex"):
                dex_data = zin.read(entry)
                # parse classes
                
    # Modify manifest using AxmlEditor logic in java/python or core-repackager
    # Let's run a repackage test
    print("[*] Ready to test on real phone.")

if __name__ == "__main__":
    run_test()
