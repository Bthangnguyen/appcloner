import subprocess, os, sys, time, re

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
    subprocess.run([ADB, "connect", DEVICE], capture_output=True)
    
    # 1. Download source split APKs of TikTok from device to PC if not present
    splits_dir = r"d:\Workspaces\clone app\tiktok_splits_source"
    os.makedirs(splits_dir, exist_ok=True)
    
    log("[*] Checking TikTok splits source on PC...")
    if len(os.listdir(splits_dir)) < 10:
        log("[*] Pulling original TikTok splits from device...")
        orig_tiktok_path = subprocess.run([ADB, "-s", DEVICE, "shell", "pm", "path", "com.ss.android.ugc.trill"], capture_output=True, text=True).stdout
        for line in orig_tiktok_path.split("\n"):
            line = line.strip()
            if line.startswith("package:"):
                apk_device_path = line.replace("package:", "")
                fname = os.path.basename(apk_device_path)
                if "base.apk" in fname:
                    subprocess.run([ADB, "-s", DEVICE, "pull", apk_device_path, r"d:\Workspaces\clone app\tiktok_base.apk"], capture_output=True)
                else:
                    subprocess.run([ADB, "-s", DEVICE, "pull", apk_device_path, os.path.join(splits_dir, fname)], capture_output=True)
    
    log(f"[*] Total TikTok splits source on PC: {len(os.listdir(splits_dir))}")

    # 2. Run CliCloner for TikTok
    cp = f"{APP_CLASSES};{APP_JAR};{KOTLIN_STDLIB};{ANDROID_JAR}"
    log("[*] Running CliCloner for TikTok...")
    res = subprocess.run([JAVA, "-cp", cp, "com.cloner.tools.CliCloner", "tiktok"], capture_output=True, text=True, encoding="utf-8", errors="replace")
    log(f"CliCloner stdout:\n{res.stdout}")
    log(f"CliCloner stderr:\n{res.stderr}")

    # 3. Push cloned APKs to device and install
    out_base = r"d:\Workspaces\clone app\cli_tiktok_cloned.apk"
    out_splits_dir = r"d:\Workspaces\clone app\cli_tiktok_cloned_splits"
    
    if os.path.exists(out_base):
        log("[*] Installing cloned TikTok to device via PackageInstaller Session...")
        subprocess.run([ADB, "-s", DEVICE, "uninstall", "com.ss.android.ugc.trill.clone1"], capture_output=True)
        subprocess.run([ADB, "-s", DEVICE, "shell", "rm", "-rf", "/data/local/tmp/tiktok_test; mkdir -p /data/local/tmp/tiktok_test"])
        subprocess.run([ADB, "-s", DEVICE, "push", out_base, "/data/local/tmp/tiktok_test/base.apk"], capture_output=True)
        
        for f in os.listdir(out_splits_dir):
            if f.endswith(".apk"):
                subprocess.run([ADB, "-s", DEVICE, "push", os.path.join(out_splits_dir, f), f"/data/local/tmp/tiktok_test/{f}"], capture_output=True)
                
        # Session install
        res_create = subprocess.run([ADB, "-s", DEVICE, "shell", "pm", "install-create", "-r"], capture_output=True, text=True)
        log(f"Create session: {res_create.stdout.strip()}")
        match = re.search(r"\[(\d+)\]", res_create.stdout)
        if match:
            sid = match.group(1)
            files = subprocess.run([ADB, "-s", DEVICE, "shell", "ls", "/data/local/tmp/tiktok_test/"], capture_output=True, text=True).stdout.split()
            for f in files:
                fpath = f"/data/local/tmp/tiktok_test/{f}"
                fsize = subprocess.run([ADB, "-s", DEVICE, "shell", "wc", "-c", fpath], capture_output=True, text=True).stdout.strip().split()[0]
                subprocess.run([ADB, "-s", DEVICE, "shell", "pm", "install-write", "-S", fsize, sid, f, fpath])
            
            res_commit = subprocess.run([ADB, "-s", DEVICE, "shell", "pm", "install-commit", sid], capture_output=True, text=True)
            log(f"Install commit: {res_commit.stdout.strip()} {res_commit.stderr.strip()}")
            
            if "Success" in res_commit.stdout:
                log("[*] Launching TikTok clone...")
                subprocess.run([ADB, "-s", DEVICE, "logcat", "-c"])
                start_res = subprocess.run([ADB, "-s", DEVICE, "shell", "am", "start", "-W", "-n", "com.ss.android.ugc.trill.clone1/com.ss.android.ugc.aweme.splash.SplashActivity"], capture_output=True, text=True)
                log(f"TikTok start output:\n{start_res.stdout.strip()}")
                
                time.sleep(3)
                ps_res = subprocess.run([ADB, "-s", DEVICE, "shell", "ps", "-A"], capture_output=True, text=True).stdout
                running = any("com.ss.android.ugc.trill.clone1" in l for l in ps_res.split("\n"))
                log("\n" + "="*80)
                log(f"*** TIKTOK IS RUNNING: {running} ***")
                log("="*80)

if __name__ == "__main__":
    main()
