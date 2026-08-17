import os, sys, zipfile, subprocess

BASE_DIR = r"D:\Workspaces\clone app"
SDK_DIR = r"D:\UserProfile\AppData\Local\Android\Sdk"
BUILD_TOOLS_DIR = os.path.join(SDK_DIR, "build-tools", "34.0.0")
AAPT2 = os.path.join(BUILD_TOOLS_DIR, "aapt2.exe")
APKSIGNER = os.path.join(BUILD_TOOLS_DIR, "apksigner.bat")

print("Checking test environment...")
