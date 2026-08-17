import subprocess, os, sys, time

TOOLS_DIR = r"d:\Workspaces\clone app\tools"
JAVA = os.path.join(TOOLS_DIR, "jdk17", "bin", "java.exe")
KOTLIN_STDLIB = os.path.join(TOOLS_DIR, "kotlinc", "lib", "kotlin-stdlib.jar")
ANDROID_JAR = r"D:\UserProfile\AppData\Local\Android\Sdk\platforms\android-34\android.jar"
APP_CLASSES = r"d:\Workspaces\clone app\AppClonerProject\build_out\app_classes"
APP_JAR = r"d:\Workspaces\clone app\AppClonerProject\build_out\app_classes.jar"
ADB = r"D:\UserProfile\AppData\Local\Android\Sdk\platform-tools\adb.exe"
DEVICE = "192.168.1.97:34879"

def log(s):
    sys.stdout.buffer.write((s + "\n").encode("utf-8", errors="replace"))
    sys.stdout.flush()

def main():
    # 1. Run CliCloner for deviceinfo
    cp = f"{APP_CLASSES};{APP_JAR};{KOTLIN_STDLIB};{ANDROID_JAR}"
    log("[*] Running CliCloner for Device Info HW...")
    res = subprocess.run([JAVA, "-cp", cp, "com.cloner.tools.CliCloner", "deviceinfo"], capture_output=True, text=True, encoding="utf-8", errors="replace")
    log(f"CliCloner stdout:\n{res.stdout}")
    log(f"CliCloner stderr:\n{res.stderr}")

    # 2. Install to phone
    out_apk = r"d:\Workspaces\clone app\cli_deviceinfo_cloned.apk"
    if os.path.exists(out_apk):
        log(f"[*] APK exists ({os.path.getsize(out_apk)} bytes). Uninstalling old clone and installing new APK...")
        subprocess.run([ADB, "connect", DEVICE], capture_output=True)
        subprocess.run([ADB, "-s", DEVICE, "uninstall", "ru.andr7e.deviceinfohw.clone1"], capture_output=True)
        inst = subprocess.run([ADB, "-s", DEVICE, "install", "-r", out_apk], capture_output=True, text=True)
        log(f"Install result:\n{inst.stdout}\n{inst.stderr}")
        
        # Launch
        subprocess.run([ADB, "-s", DEVICE, "logcat", "-c"])
        start = subprocess.run([ADB, "-s", DEVICE, "shell", "am", "start", "-W", "-n", "ru.andr7e.deviceinfohw.clone1/ru.andr7e.deviceinfohw.MainActivity"], capture_output=True, text=True)
        log(f"Launch result:\n{start.stdout}")
        
        time.sleep(3)
        ps = subprocess.run([ADB, "-s", DEVICE, "shell", "ps", "-A"], capture_output=True, text=True).stdout
        running = any("ru.andr7e.deviceinfohw.clone1" in l for l in ps.split("\n"))
        log(f"\n=======================================================")
        log(f"*** DEVICE INFO HW IS RUNNING: {running} ***")
        log(f"=======================================================")

if __name__ == "__main__":
    main()
