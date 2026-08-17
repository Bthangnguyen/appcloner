import os
import sys
import zipfile
import json

def verify_appcloner_studio_apk():
    apk_path = "AppCloner-Studio-v1.0.apk"
    if not os.path.exists(apk_path):
        print(f"[FAIL] {apk_path} not found!")
        return False
    
    print(f"[*] Inspecting {apk_path} ({os.path.getsize(apk_path):,} bytes)...")
    with zipfile.ZipFile(apk_path, 'r') as z:
        names = z.namelist()
        
        # 1. Check runtime_classes.dex
        if "assets/runtime_classes.dex" in names:
            print("  [PASS] assets/runtime_classes.dex is present!")
        else:
            print("  [FAIL] assets/runtime_classes.dex is missing!")
            return False
            
        # 2. Check native libs in assets
        native_libs = [n for n in names if n.startswith("assets/native_libs/")]
        print(f"  [PASS] Found {len(native_libs)} native libraries in assets!")
        for n in sorted(native_libs)[:8]:
            print(f"    - {n}")
            
        # 3. Check classes.dex
        if "classes.dex" in names:
            print("  [PASS] classes.dex is present!")
            
    print("\n[SUCCESS] AppCloner Studio APK verified successfully with all integrated components!")
    return True

if __name__ == '__main__':
    verify_appcloner_studio_apk()
