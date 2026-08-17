import subprocess
import time

ADB = r"D:\UserProfile\AppData\Local\Android\Sdk\platform-tools\adb.exe"
DEVICE = "192.168.1.97:34879"

def main():
    print("[1] Connecting to device...")
    subprocess.run([ADB, "connect", DEVICE], capture_output=True, text=True)
    time.sleep(1)
    
    # Check device state
    devs = subprocess.run([ADB, "devices"], capture_output=True, text=True).stdout
    print(devs)
    if DEVICE not in devs or "device" not in devs:
        print("[!] Device not connected yet!")
        return

    # Start continuous logcat process
    print("[2] Starting Logcat listener...")
    log_proc = subprocess.Popen(
        [ADB, "-s", DEVICE, "logcat", "-v", "threadtime"],
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        text=True,
        encoding="utf-8",
        errors="replace"
    )

    time.sleep(1)

    print("[3] Launching ru.andr7e.deviceinfohw.clone1...")
    start_res = subprocess.run(
        [ADB, "-s", DEVICE, "shell", "am", "start", "-n", "ru.andr7e.deviceinfohw.clone1/ru.andr7e.deviceinfohw.MainActivity"],
        capture_output=True,
        text=True
    )
    print("Start command result:", start_res.stdout.strip())

    # Collect logs for 4 seconds
    print("[4] Collecting logs for 4 seconds...")
    collected_lines = []
    start_time = time.time()
    while time.time() - start_time < 4.0:
        line = log_proc.stdout.readline()
        if line:
            collected_lines.append(line.strip())

    log_proc.terminate()

    print(f"\n[5] Analyzed {len(collected_lines)} log lines during app launch.\n")
    print("=" * 80)
    print("RELEVANT CRASH / ACTIVITY LOGS:")
    print("=" * 80)

    matched = [
        l for l in collected_lines 
        if any(k in l for k in [
            "ru.andr7e", "deviceinfohw", "FATAL", "AndroidRuntime", 
            "DEBUG", "Zygote", "ActivityTaskManager", "died", "kill", 
            "Exception", "Error", "finish", "exit"
        ])
    ]

    for m in matched:
        print(m)

if __name__ == "__main__":
    main()
