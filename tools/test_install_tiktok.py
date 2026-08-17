import subprocess
import re

ADB = r"D:\UserProfile\AppData\Local\Android\Sdk\platform-tools\adb.exe"
DEVICE = "192.168.1.97:34879"

def run_cmd(cmd):
    return subprocess.run([ADB, "-s", DEVICE] + cmd, capture_output=True, text=True, encoding="utf-8", errors="replace")

def test_install():
    subprocess.run([ADB, "connect", DEVICE], capture_output=True)
    # 1. Create install session
    res = run_cmd(["shell", "pm", "install-create", "-r"])
    print("Create session output:", res.stdout.strip())
    match = re.search(r"\[(\d+)\]", res.stdout)
    if not match:
        print("Failed to parse session ID!")
        return

    session_id = match.group(1)
    print(f"[*] Session ID: {session_id}")

    # 2. Write base.apk
    base_path = "/storage/emulated/0/Android/data/com.cloner.app/files/clones/com.ss.android.ugc.trill.clone1.apk"
    base_size = run_cmd(["shell", "wc", "-c", base_path]).stdout.strip().split()[0]
    print(f"Writing base.apk ({base_size} bytes)...")
    w_res = run_cmd(["shell", "pm", "install-write", "-S", base_size, session_id, "base.apk", base_path])
    print(f"Write base result: {w_res.stdout.strip()} {w_res.stderr.strip()}")

    # 3. List splits
    splits_dir = "/storage/emulated/0/Android/data/com.cloner.app/files/clones/com.ss.android.ugc.trill.clone1_splits"
    ls_res = run_cmd(["shell", "ls", splits_dir]).stdout.strip().split()
    print(f"Found {len(ls_res)} split files.")

    for i, sfile in enumerate(ls_res):
        spath = f"{splits_dir}/{sfile}"
        ssize = run_cmd(["shell", "wc", "-c", spath]).stdout.strip().split()[0]
        # Clean split name
        clean_name = f"split_{i}.apk"
        sw_res = run_cmd(["shell", "pm", "install-write", "-S", ssize, session_id, clean_name, spath])
        if "Success" not in sw_res.stdout:
            print(f"Write split {sfile} failed: {sw_res.stdout} {sw_res.stderr}")

    # 4. Commit session
    print("Committing session...")
    c_res = run_cmd(["shell", "pm", "install-commit", session_id])
    print("\n" + "=" * 80)
    print("COMMIT RESULT:")
    print("=" * 80)
    print("STDOUT:", c_res.stdout.strip())
    print("STDERR:", c_res.stderr.strip())
    print("=" * 80)

if __name__ == "__main__":
    test_install()
