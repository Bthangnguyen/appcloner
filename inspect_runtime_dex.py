import zipfile, lzma, os, struct

with zipfile.ZipFile('AppCloner-3.6.8-26062918.apk', 'r') as z:
    for name in ['assets/classes.dex.xz', 'assets/secondary/classes.dex.xz', 'assets/bc/classes.dex.xz', 'assets/kotlin.dex.xz']:
        if name in z.namelist():
            raw_xz = z.read(name)
            decompressed = lzma.decompress(raw_xz)
            safe_name = name.replace('/', '_').replace('.xz', '')
            out_path = f"extracted_{safe_name}"
            with open(out_path, 'wb') as f:
                f.write(decompressed)
            print(f"Extracted {name} -> {out_path} ({len(decompressed):,} bytes)")

# Let's inspect strings inside extracted_assets_classes.dex
def dump_dex_classes(dex_path):
    print(f"\n--- Classes in {dex_path} ---")
    with open(dex_path, 'rb') as f:
        dex_bytes = f.read()
    
    if len(dex_bytes) < 112 or dex_bytes[:3] != b'dex':
        print("Invalid DEX")
        return
        
    string_ids_size = struct.unpack('<I', dex_bytes[56:60])[0]
    string_ids_off = struct.unpack('<I', dex_bytes[60:64])[0]
    type_ids_size = struct.unpack('<I', dex_bytes[64:68])[0]
    type_ids_off = struct.unpack('<I', dex_bytes[68:72])[0]
    class_defs_size = struct.unpack('<I', dex_bytes[96:100])[0]
    class_defs_off = struct.unpack('<I', dex_bytes[100:104])[0]
    
    # Read string table
    strings = []
    for i in range(string_ids_size):
        off = struct.unpack('<I', dex_bytes[string_ids_off + i*4 : string_ids_off + (i+1)*4])[0]
        idx = off
        while dex_bytes[idx] & 0x80:
            idx += 1
        idx += 1
        end = idx
        while end < len(dex_bytes) and dex_bytes[end] != 0:
            end += 1
        strings.append(dex_bytes[idx:end].decode('utf-8', errors='replace'))
        
    type_strings = []
    for i in range(type_ids_size):
        str_idx = struct.unpack('<I', dex_bytes[type_ids_off + i*4 : type_ids_off + (i+1)*4])[0]
        type_strings.append(strings[str_idx])
        
    classes = []
    for i in range(class_defs_size):
        class_idx = struct.unpack('<I', dex_bytes[class_defs_off + i*32 : class_defs_off + i*32 + 4])[0]
        cname = type_strings[class_idx]
        classes.append(cname)
        
    print(f"Total classes defined: {len(classes)}")
    # Print sample interesting classes
    for c in classes[:40]:
        print("  ", c)
    if len(classes) > 40:
        print(f"  ... and {len(classes) - 40} more classes")

if os.path.exists('extracted_assets_classes.dex'):
    dump_dex_classes('extracted_assets_classes.dex')
if os.path.exists('extracted_assets_secondary_classes.dex'):
    dump_dex_classes('extracted_assets_secondary_classes.dex')
