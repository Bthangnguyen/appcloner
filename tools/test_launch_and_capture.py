import subprocess
import time

ADB = r"D:\UserProfile\AppData\Local\Android\Sdk\platform-tools\adb.exe"
DEVICE = "192.168.1.97:34879"

def run_adb(args):
    cmd = [ADB, "-s", DEVICE] + args
    return subprocess.run(cmd, capture_output=True, text=True, encoding="utf-8", errors="replace")

def test_clone_app():
    pkg = "ru.andr7e.deviceinfohw.clone1"
    print(f"[*] Testing launch of cloned app: {pkg}...")
    
    # 1. Force stop and clear logcat
    run_adb(["shell", "am", "force-stop", pkg])
    run_adb(["logcat", "-c"])
    time.sleep(0.5)
    
    # 2. Launch cloned app
    print("[*] Launching app via monkey...")
    launch_res = run_adb(["shell", "monkey", "-p", pkg, "1"])
    print(f"Launch output: {launch_res.stdout.strip()}")
    
    # Wait for startup / crash
    time.sleep(1.5)
    
    # 3. Capture logcat
    print("\n" + "=" * 80)
    print(f"CAPTURING LOGCAT FOR {pkg}:")
    print("=" * 80)
    
    log_res = run_adb(["logcat", "-d", "-v", "threadtime", "AndroidRuntime:E", "ActivityManager:I", "DEBUG:E", "libc:F", "*:S"])
    log_lines = log_res.stdout.split("\n")
    print(f"Total error/runtime log lines: {len(log_lines)}")
    for line in log_lines[-100:]:
        print(line)
        
    # Also dump all logs mentioning the package name
    pkg_logs = run_adb(["logcat", "-d", "-v", "time"])
    relevant_lines = [l for l in pkg_logs.stdout.split("\n") if pkg in l or "deviceinfohw" in l or "FATAL" in l or "Crash" in l]
    print("\n" + "=" * 80)
    print(f"SPECIFIC PACKAGE LOGS ({len(relevant_lines)} lines):")
    print("=" * 80)
    for l in relevant_lines[-50:]:
        print(l)

if __name__ == "__main__":
    test_clone_app()
