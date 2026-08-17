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

def test_deviceinfo():
    print("\n" + "="*80)
    print("[*] TESTING DEVICE INFO HW CLONE INSTALL & LAUNCH")
    print("="*80)
    
    # 1. Pull current cloned APK from phone
    cloned_path = "/storage/emulated/0/Android/data/com.cloner.app/files/clones/ru.andr7e.deviceinfohw.clone1.apk"
    local_apk = r"d:\Workspaces\clone app\test_deviceinfo_installed.apk"
    res_pull = run_adb(["pull", cloned_path, local_apk])
    print(f"Pull result: {res_pull.stdout.strip()}")
    
    # 2. Check if file exists
    if not os.path.exists(local_apk):
        print("Cloned APK not found yet.")
        return False
        
    # 3. Install to phone
    print("Installing to phone via ADB...")
    res_inst = run_adb(["install", "-r", local_apk])
    print(f"Install output: {res_inst.stdout.strip()} {res_inst.stderr.strip()}")
    
    # 4. Launch app
    print("Launching ru.andr7e.deviceinfohw.clone1...")
    res_start = run_adb(["shell", "am", "start", "-W", "-n", "ru.andr7e.deviceinfohw.clone1/ru.andr7e.deviceinfohw.MainActivity"])
    print(f"Start output:\n{res_start.stdout.strip()}")
    
    time.sleep(2)
    
    # 5. Check process
    res_ps = run_adb(["shell", "ps", "-A"])
    is_running = any("ru.andr7e.deviceinfohw.clone1" in l for l in res_ps.stdout.split("\n"))
    print(f"Is running: {is_running}")
    return is_running

if __name__ == "__main__":
    subprocess.run([ADB, "connect", DEVICE], capture_output=True)
    test_deviceinfo()
