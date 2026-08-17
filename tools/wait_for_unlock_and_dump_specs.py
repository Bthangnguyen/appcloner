import subprocess, time, os, sys
import xml.etree.ElementTree as ET

ADB = r"D:\UserProfile\AppData\Local\Android\Sdk\platform-tools\adb.exe"
DEVICE = "192.168.1.97:34879"

def run_adb(args):
    return subprocess.run([ADB, "-s", DEVICE] + args, capture_output=True, text=True, errors="replace").stdout

subprocess.run([ADB, "connect", DEVICE], capture_output=True)

print("[*] Waiting for device screen to be unlocked by user...")
for i in range(30):
    dumpsys = run_adb(["shell", "dumpsys", "window"])
    is_locked = "mDreamingLockscreen=true" in dumpsys or "mKeyguardShowing=true" in dumpsys
    if not is_locked:
        print("[+] Screen is UNLOCKED!")
        break
    time.sleep(1)

# Launch Device Info HW
print("[*] Launching Device Info HW Clone...")
run_adb(["shell", "am", "start", "-n", "ru.andr7e.deviceinfohw.clone1/ru.andr7e.deviceinfohw.MainActivity"])
time.sleep(3)

# Dump UI Hierarchy
print("[*] Dumping UI hierarchy...")
run_adb(["shell", "uiautomator", "dump", "/data/local/tmp/dump.xml"])
subprocess.run([ADB, "-s", DEVICE, "pull", "/data/local/tmp/dump.xml", r"d:\Workspaces\clone app\specs_dump.xml"], capture_output=True)

# Capture clean screenshot
subprocess.run([ADB, "-s", DEVICE, "shell", "screencap", "-p", "/sdcard/Download/specs_screenshot.png"], capture_output=True)
subprocess.run([ADB, "-s", DEVICE, "pull", "/sdcard/Download/specs_screenshot.png", r"d:\Workspaces\clone app\specs_screenshot.png"], capture_output=True)

if os.path.exists(r"d:\Workspaces\clone app\specs_dump.xml"):
    tree = ET.parse(r"d:\Workspaces\clone app\specs_dump.xml")
    root = tree.getroot()
    print("=================== DEVICE INFO HW SPECS DISPLAYED ON SCREEN ===================")
    for elem in root.iter():
        text = elem.attrib.get("text", "").strip()
        if text and len(text) > 1:
            try:
                print(f"  [>] {text}")
            except Exception:
                pass
    print("================================================================================")
