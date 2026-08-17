import os
import sys
import subprocess
import zipfile
import time

BASE_DIR = r"D:\Workspaces\clone app"
PROJECT_DIR = os.path.join(BASE_DIR, "AppClonerProject")
TOOLS_DIR = os.path.join(BASE_DIR, "tools")
BUILD_DIR = os.path.join(PROJECT_DIR, "build_out")

JDK_BIN = os.path.join(TOOLS_DIR, "jdk17", "bin")
JAVA = os.path.join(JDK_BIN, "java.exe")
KOTLIN_STDLIB_JAR = os.path.join(TOOLS_DIR, "kotlinc", "lib", "kotlin-stdlib.jar")
SDK_DIR = r"D:\UserProfile\AppData\Local\Android\Sdk"
ANDROID_JAR = os.path.join(SDK_DIR, "platforms", "android-34", "android.jar")
ADB = os.path.join(SDK_DIR, "platform-tools", "adb.exe")
DEVICE = "192.168.1.97:34879"

# Classpath for running ClonePipeline
cp = f"{BUILD_DIR}\\app_classes;{ANDROID_JAR};{KOTLIN_STDLIB_JAR}"

print("[*] Repackaging CLI test tool ready.")
