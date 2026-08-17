import os
import sys
import zipfile
import subprocess
import hashlib
import base64
from datetime import datetime, timezone

APKSIGNER = r"D:\UserProfile\AppData\Local\Android\Sdk\build-tools\34.0.0\apksigner.bat"
ZIPALIGN = r"D:\UserProfile\AppData\Local\Android\Sdk\build-tools\34.0.0\zipalign.exe"
ADB = r"D:\UserProfile\AppData\Local\Android\Sdk\platform-tools\adb.exe"
DEVICE = "192.168.1.97:34879"

def der_len(l):
    if l < 128: return bytes([l])
    elif l < 256: return bytes([0x81, l])
    elif l < 65536: return bytes([0x82, (l >> 8) & 0xFF, l & 0xFF])
    else: return bytes([0x83, (l >> 16) & 0xFF, (l >> 8) & 0xFF, l & 0xFF])

def der_seq(c): return bytes([0x30]) + der_len(len(c)) + c
def der_set(c): return bytes([0x31]) + der_len(len(c)) + c
def der_oid(o): return bytes([0x06]) + der_len(len(o)) + o
def der_null(): return bytes([0x05, 0x00])
def der_int(v): return bytes([0x02, 0x01, v])
def der_octet(b): return bytes([0x04]) + der_len(len(b)) + b
def der_utf8(s): b = s.encode('utf-8'); return bytes([0x0C]) + der_len(len(b)) + b
def der_bitstr(b): return bytes([0x03]) + der_len(len(b) + 1) + bytes([0x00]) + b
def der_explicit(tag, c): return bytes([0xA0 | tag]) + der_len(len(c)) + c

print("[*] ASN.1 DER helpers ready.")
