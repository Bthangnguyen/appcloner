import os, subprocess, sys, zipfile

sys.stdout.reconfigure(encoding='utf-8')

BASE_DIR = r"D:\Workspaces\clone app"
PROJECT_DIR = os.path.join(BASE_DIR, "AppClonerProject")
TOOLS_DIR = os.path.join(BASE_DIR, "tools")
BUILD_DIR = os.path.join(PROJECT_DIR, "build_out")
os.makedirs(BUILD_DIR, exist_ok=True)

# Cong cu
JDK_BIN = os.path.join(TOOLS_DIR, "jdk17", "bin")
JAVA = os.path.join(JDK_BIN, "java.exe")
KEYTOOL = os.path.join(JDK_BIN, "keytool.exe")
KOTLIN_COMPILER_JAR = os.path.join(TOOLS_DIR, "kotlinc", "lib", "kotlin-compiler.jar")
KOTLIN_STDLIB_JAR = os.path.join(TOOLS_DIR, "kotlinc", "lib", "kotlin-stdlib.jar")

SDK_DIR = r"D:\UserProfile\AppData\Local\Android\Sdk"
BUILD_TOOLS_DIR = os.path.join(SDK_DIR, "build-tools", "34.0.0")
AAPT2 = os.path.join(BUILD_TOOLS_DIR, "aapt2.exe")
D8 = os.path.join(BUILD_TOOLS_DIR, "d8.bat")
ZIPALIGN = os.path.join(BUILD_TOOLS_DIR, "zipalign.exe")
APKSIGNER = os.path.join(BUILD_TOOLS_DIR, "apksigner.bat")
ANDROID_JAR = os.path.join(SDK_DIR, "platforms", "android-34", "android.jar")

def run(cmd, desc):
    print(f"--> [STEP] {desc}...")
    env = os.environ.copy()
    env["JAVA_HOME"] = os.path.join(TOOLS_DIR, "jdk17")
    env["PATH"] = JDK_BIN + ";" + env.get("PATH", "")
    res = subprocess.run(cmd, shell=True, env=env, cwd=PROJECT_DIR, capture_output=True, text=True)
    if res.returncode != 0:
        print(f"[ERROR in {desc}]:\n{res.stderr}\n{res.stdout}")
        sys.exit(1)
    else:
        print(f"    [OK] {desc} SUCCESS!")

# 1. Compile Runtime Module sang runtime_classes.dex
runtime_src_dir = os.path.join(PROJECT_DIR, "clone-runtime", "src", "main", "java")
runtime_sources = []
for root, dirs, files in os.walk(runtime_src_dir):
    for f in files:
        if f.endswith(".kt") or f.endswith(".java"):
            runtime_sources.append(os.path.join(root, f))

runtime_classes_dir = os.path.join(BUILD_DIR, "runtime_classes")
os.makedirs(runtime_classes_dir, exist_ok=True)
runtime_sources_arg = " ".join([f'"{sf}"' for sf in runtime_sources])
run(f'"{JAVA}" -jar "{KOTLIN_COMPILER_JAR}" -cp "{ANDROID_JAR};{KOTLIN_STDLIB_JAR}" -d "{runtime_classes_dir}" -jvm-target 1.8 {runtime_sources_arg}', "Compile Clone Runtime Classes")

runtime_jar = os.path.join(BUILD_DIR, "runtime_classes.jar")
with zipfile.ZipFile(runtime_jar, 'w', zipfile.ZIP_DEFLATED) as jout:
    for root, dirs, files in os.walk(runtime_classes_dir):
        for f in files:
            fp = os.path.join(root, f)
            rel = os.path.relpath(fp, runtime_classes_dir)
            jout.write(fp, rel)

runtime_dex_dir = os.path.join(BUILD_DIR, "runtime_dex")
os.makedirs(runtime_dex_dir, exist_ok=True)
run(f'"{D8}" --lib "{ANDROID_JAR}" --min-api 21 --output "{runtime_dex_dir}" "{runtime_jar}" "{KOTLIN_STDLIB_JAR}"', "Convert Runtime to runtime_classes.dex")

runtime_dex_file = os.path.join(runtime_dex_dir, "classes.dex")
assets_dir = os.path.join(PROJECT_DIR, "app", "src", "main", "assets")
os.makedirs(assets_dir, exist_ok=True)
assets_runtime_dex = os.path.join(assets_dir, "runtime_classes.dex")
with open(runtime_dex_file, "rb") as fin, open(assets_runtime_dex, "wb") as fout:
    fout.write(fin.read())

print("    [OK] Generated assets/runtime_classes.dex!")

# 2. Compile Resources bang AAPT2
res_zip = os.path.join(BUILD_DIR, "compiled_res.zip")
res_dir = os.path.join(PROJECT_DIR, "app", "src", "main", "res")
manifest_xml = os.path.join(PROJECT_DIR, "app", "src", "main", "AndroidManifest.xml")
gen_dir = os.path.join(BUILD_DIR, "gen")
os.makedirs(gen_dir, exist_ok=True)

run(f'"{AAPT2}" compile --dir "{res_dir}" -o "{res_zip}"', "Compile Resources (AAPT2)")

# 3. Link Resources va sinh R.java voi Target SDK 34
res_apk = os.path.join(BUILD_DIR, "resources.apk")
run(f'"{AAPT2}" link -I "{ANDROID_JAR}" --min-sdk-version 21 --target-sdk-version 34 --manifest "{manifest_xml}" --java "{gen_dir}" -o "{res_apk}" "{res_zip}" --auto-add-overlay', "Link Resources (AAPT2 link)")

# 4. Thu thap tat ca cac file source Kotlin & Java
source_files = []
for root, dirs, files in os.walk(PROJECT_DIR):
    if "build_out" in root: continue
    for f in files:
        if f.endswith(".kt") or f.endswith(".java"):
            source_files.append(os.path.join(root, f))

# Them R.java
for root, dirs, files in os.walk(gen_dir):
    for f in files:
        if f.endswith(".java"):
            source_files.append(os.path.join(root, f))

print(f"Total source files to compile: {len(source_files)}")

# 5. Bien dich Kotlin / Java sang .class
classes_dir = os.path.join(BUILD_DIR, "classes")
os.makedirs(classes_dir, exist_ok=True)
sources_arg = " ".join([f'"{sf}"' for sf in source_files])
run(f'"{JAVA}" -jar "{KOTLIN_COMPILER_JAR}" -cp "{ANDROID_JAR};{gen_dir};{KOTLIN_STDLIB_JAR}" -d "{classes_dir}" -jvm-target 1.8 {sources_arg}', "Compile Kotlin/Java to Class Bytecode")

# 6. Dong goi classes_dir thanh classes.jar cho D8
classes_jar = os.path.join(BUILD_DIR, "app_classes.jar")
with zipfile.ZipFile(classes_jar, 'w', zipfile.ZIP_DEFLATED) as jout:
    for root, dirs, files in os.walk(classes_dir):
        for f in files:
            fp = os.path.join(root, f)
            rel = os.path.relpath(fp, classes_dir)
            jout.write(fp, rel)

print("    [OK] Packaged classes into app_classes.jar!")

# 7. D8: Chuyen doi classes.jar + kotlin-stdlib.jar sang classes.dex
dex_dir = os.path.join(BUILD_DIR, "dex")
os.makedirs(dex_dir, exist_ok=True)
run(f'"{D8}" --lib "{ANDROID_JAR}" --min-api 21 --output "{dex_dir}" "{classes_jar}" "{KOTLIN_STDLIB_JAR}"', "Convert Class to DEX (D8)")

# 8. Gop classes.dex va assets vao resources.apk
dex_file = os.path.join(dex_dir, "classes.dex")
unaligned_apk = os.path.join(BUILD_DIR, "unaligned.apk")

with zipfile.ZipFile(res_apk, 'r') as zin, zipfile.ZipFile(unaligned_apk, 'w') as zout:
    for item in zin.infolist():
        zout.writestr(item, zin.read(item.filename))
    zout.write(dex_file, "classes.dex")
    for root, dirs, files in os.walk(assets_dir):
        for f in files:
            fp = os.path.join(root, f)
            rel = os.path.relpath(fp, assets_dir).replace("\\", "/")
            zout.write(fp, f"assets/{rel}")

print("    [OK] Added classes.dex, runtime_classes.dex, and native_libs into APK package!")

# 9. Zipalign can chinh 4-byte
aligned_apk = os.path.join(BUILD_DIR, "aligned.apk")
run(f'"{ZIPALIGN}" -f 4 "{unaligned_apk}" "{aligned_apk}"', "Memory alignment (Zipalign)")

# 10. Ky so APK bang Debug Keystore
keystore_path = os.path.join(BUILD_DIR, "debug.keystore")
if not os.path.exists(keystore_path):
    run(f'"{KEYTOOL}" -genkeypair -v -keystore "{keystore_path}" -alias androiddebugkey -keypass android -storepass android -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Android Debug,O=Android,C=US"', "Generate Debug Keystore")

final_apk = os.path.join(BASE_DIR, "AppCloner-Studio-v1.0.apk")
run(f'"{APKSIGNER}" sign --ks "{keystore_path}" --ks-pass pass:android --ks-key-alias androiddebugkey --key-pass pass:android --out "{final_apk}" "{aligned_apk}"', "Sign APK (ApkSigner v1/v2)")

print("\n=======================================================")
print(f"SUCCESSFULLY COMPILED & BUILT APK!")
print(f"File Path: {final_apk}")
print(f"File Size: {os.path.getsize(final_apk):,} bytes")
print("=======================================================")
