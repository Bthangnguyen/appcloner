import subprocess
import time
import os
import re

ADB = r"D:\UserProfile\AppData\Local\Android\Sdk\platform-tools\adb.exe"
DEVICE = "192.168.1.97:34879"

def run_adb(cmd):
    return subprocess.run([ADB, "-s", DEVICE] + cmd, capture_output=True, text=True, encoding="utf-8", errors="replace")

def test_install_and_launch():
    subprocess.run([ADB, "connect", DEVICE], capture_output=True)
    
    # 1. Check Device Info HW
    print("="*80)
    print("[1] TESTING DEVICE INFO HW CLONE")
    print("="*80)
    dev_apk = "/storage/emulated/0/Android/data/com.cloner.app/files/clones/ru.andr7e.deviceinfohw.clone1.apk"
    local_dev = r"d:\Workspaces\clone app\device_info_current.apk"
    run_adb(["pull", dev_apk, local_dev])
    
    if os.path.exists(local_dev):
        inst_res = run_adb(["install", "-r", local_dev])
        print(f"Device Info HW install: {inst_res.stdout.strip()} {inst_res.stderr.strip()}")
        
        # Clear logcat & Launch
        run_adb(["logcat", "-c"])
        start_res = run_adb(["shell", "am", "start", "-W", "-n", "ru.andr7e.deviceinfohw.clone1/ru.andr7e.deviceinfohw.MainActivity"])
        print(f"Device Info HW start:\n{start_res.stdout.strip()}")
        
        time.sleep(2)
        ps_res = run_adb(["shell", "ps", "-A"]).stdout
        running = any("ru.andr7e.deviceinfohw.clone1" in l for l in ps_res.split("\n"))
        print(f"Device Info HW is alive: {running}")

    # 2. Check TikTok
    print("\n" + "="*80)
    print("[2] TESTING TIKTOK CLONE")
    print("="*80)
    run_adb(["shell", "rm", "-rf", "/data/local/tmp/tiktok_test; mkdir -p /data/local/tmp/tiktok_test"])
    run_adb(["shell", "cp", "/storage/emulated/0/Android/data/com.cloner.app/files/clones/com.ss.android.ugc.trill.clone1.apk", "/data/local/tmp/tiktok_test/base.apk"])
    run_adb(["shell", "cp /storage/emulated/0/Android/data/com.cloner.app/files/clones/com.ss.android.ugc.trill.clone1_splits/*.apk /data/local/tmp/tiktok_test/"])
    
    res_create = run_adb(["shell", "pm", "install-create", "-r"])
    print("TikTok create session:", res_create.stdout.strip())
    match = re.search(r"\[(\d+)\]", res_create.stdout)
    if match:
        sid = match.group(1)
        files = run_adb(["shell", "ls", "/data/local/tmp/tiktok_test/"]).stdout.split()
        for f in files:
            fpath = f"/data/local/tmp/tiktok_test/{f}"
            fsize = run_adb(["shell", "wc", "-c", fpath]).stdout.strip().split()[0]
            run_adb(["shell", "pm", "install-write", "-S", fsize, sid, f, fpath])
            
        res_commit = run_adb(["shell", "pm", "install-commit", sid])
        print("TikTok install commit:", res_commit.stdout.strip(), res_commit.stderr.strip())
        
        if "Success" in res_commit.stdout:
            print("Launching TikTok clone...")
            t_start = run_adb(["shell", "am", "start", "-W", "-n", "com.ss.android.ugc.trill.clone1/com.ss.android.ugc.aweme.splash.SplashActivity"])
            print(f"TikTok start:\n{t_start.stdout.strip()}")
            time.sleep(3)
            t_ps = run_adb(["shell", "ps", "-A"]).stdout
            t_running = any("com.ss.android.ugc.trill.clone1" in l for l in t_ps.split("\n"))
            print(f"TikTok is alive: {t_running}")

if __name__ == "__main__":
    test_install_and_launch()
