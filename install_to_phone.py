import subprocess
import sys
import time
import re

if hasattr(sys.stdout, 'reconfigure'):
    sys.stdout.reconfigure(encoding='utf-8')
if hasattr(sys.stderr, 'reconfigure'):
    sys.stderr.reconfigure(encoding='utf-8')

adb = r"D:\UserProfile\AppData\Local\Android\Sdk\platform-tools\adb.exe"

print("--> Đang tìm thiết bị Android qua mDNS / Wi-Fi...")
found = False

for attempt in range(15):
    dev_out = subprocess.run([adb, "devices", "-l"], capture_output=True, text=True).stdout
    if "device product:" in dev_out or "SM_G981V" in dev_out or "_adb-tls-connect" in dev_out:
        print(f"[OK] Đã tìm thấy thiết bị ở giây thứ {attempt * 2}:")
        print(dev_out.strip())
        
        match = re.search(r"transport_id:(\d+)", dev_out)
        if match:
            t_id = match.group(1)
            print(f"\n--> Đang cài đặt AppCloner-Studio-v1.0.apk (transport_id={t_id})...")
            r_inst = subprocess.run([adb, "-t", t_id, "install", "-r", r"D:\Workspaces\clone app\AppCloner-Studio-v1.0.apk"], capture_output=True, text=True)
            print("KẾT QUẢ CÀI ĐẶT:", r_inst.stdout.strip(), r_inst.stderr.strip())
            
            # Cấp quyền trợ năng
            subprocess.run([adb, "-t", t_id, "shell", "appops", "set", "com.cloner.app", "ACCESS_RESTRICTED_SETTINGS", "allow"])
            subprocess.run([adb, "-t", t_id, "shell", "settings", "put", "secure", "enabled_accessibility_services", "com.cloner.app/com.cloner.app.service.TikTokAutoPostService"])
            subprocess.run([adb, "-t", t_id, "shell", "settings", "put", "secure", "accessibility_enabled", "1"])
            
            # Khởi chạy App
            r_start = subprocess.run([adb, "-t", t_id, "shell", "am", "start", "-n", "com.cloner.app/.activity.MainActivity"], capture_output=True, text=True)
            print("KHỞI CHẠY APP:", r_start.stdout.strip())
            found = True
            break
    time.sleep(1.5)

if not found:
    print("Chưa kết nối được thiết bị. Đang kiểm tra cổng kết nối...")
