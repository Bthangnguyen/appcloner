import socket, subprocess, sys

ip = '192.168.1.106'
adb = r'D:\UserProfile\AppData\Local\Android\Sdk\platform-tools\adb.exe'

print(f"Scanning open wireless debugging ports on {ip}...")
connected = False
for port in range(35000, 48000):
    s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    s.settimeout(0.04)
    if s.connect_ex((ip, port)) == 0:
        print(f"Found open port: {port}")
        res = subprocess.run(f'"{adb}" connect {ip}:{port}', shell=True, capture_output=True, text=True)
        print(f"ADB connect {ip}:{port} -> {res.stdout.strip()}")
        if "connected" in res.stdout.lower() and "cannot" not in res.stdout.lower():
            connected = True
            break
    s.close()

res = subprocess.run(f'"{adb}" devices', shell=True, capture_output=True, text=True)
print("\nDevices attached:")
print(res.stdout)
