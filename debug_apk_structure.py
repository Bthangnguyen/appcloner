import struct, zipfile, os, sys

sys.stdout.reconfigure(encoding='utf-8')

# Simulate what ClonePipeline does: read an APK, modify manifest + arsc + icons
# Let's check a real Device Info HW APK to understand its structure

# First check if we have the original App Cloner APK to see what it does differently  
apk_path = r'D:\Workspaces\clone app\AppCloner-Studio-v1.0.apk'

print("=" * 60)
print("ANALYZING APK STRUCTURE:", apk_path)
print("=" * 60)

with zipfile.ZipFile(apk_path, 'r') as z:
    manifest = z.read('AndroidManifest.xml')
    arsc = z.read('resources.arsc')
    
    # List all entries with "icon" or "launcher" in name
    icon_entries = [e for e in z.namelist() if 'icon' in e.lower() or 'launcher' in e.lower() or 'logo' in e.lower()]
    print(f"\nIcon-related entries ({len(icon_entries)}):")
    for ie in icon_entries:
        info = z.getinfo(ie)
        print(f"  {ie} ({info.file_size} bytes, method={info.compress_type})")
    
    # List all mipmap entries
    mipmap_entries = [e for e in z.namelist() if 'mipmap' in e.lower()]
    print(f"\nMipmap entries ({len(mipmap_entries)}):")
    for me in mipmap_entries:
        info = z.getinfo(me)
        print(f"  {me} ({info.file_size} bytes)")
    
    # List all drawable entries
    drawable_entries = [e for e in z.namelist() if e.startswith('res/drawable')]
    print(f"\nDrawable entries ({len(drawable_entries)}):")
    for de in drawable_entries:
        info = z.getinfo(de)
        print(f"  {de} ({info.file_size} bytes)")
    
    # Check AndroidManifest.xml content
    print(f"\nAndroidManifest.xml: {len(manifest)} bytes")
    
    # Parse AXML strings
    sp_type, sp_size, str_count, style_count, flags, str_start, style_start = struct.unpack('<IIIIIII', manifest[8:36])
    is_utf8 = bool(flags & 0x100)
    offsets = list(struct.unpack(f'<{str_count}I', manifest[36:36 + str_count*4]))
    pool_data = manifest[8 + str_start: 8 + sp_size]
    
    strings = []
    for off in offsets:
        if is_utf8:
            u16_len = pool_data[off]
            idx = off + (2 if u16_len & 0x80 else 1)
            u8_len = pool_data[idx]
            idx += 2 if u8_len & 0x80 else 1
            s = pool_data[idx:idx+u8_len].decode('utf-8', errors='replace')
        else:
            u16_len = struct.unpack('<H', pool_data[off:off+2])[0]
            s = pool_data[off+2:off+2+u16_len*2].decode('utf-16le', errors='replace')
        strings.append(s)
    
    # Print all strings that are package-like
    print(f"\nAll AXML strings ({len(strings)}):")
    for i, s in enumerate(strings):
        if s and len(s) > 1:
            print(f"  [{i}] = {repr(s)}")
    
    # Check resources.arsc
    print(f"\nresources.arsc: {len(arsc)} bytes")
    root_header_size = struct.unpack('<H', arsc[2:4])[0]
    sp_type2, sp_size2 = struct.unpack('<HI', arsc[root_header_size:root_header_size+6])
    print(f"  Root header size: {root_header_size}")
    print(f"  StringPool size: {sp_size2}")
    
    pkg_offset = root_header_size + sp_size2
    if pkg_offset < len(arsc) - 268:
        chunk_type = struct.unpack('<H', arsc[pkg_offset:pkg_offset+2])[0]
        pkg_name = arsc[pkg_offset+12:pkg_offset+12+256].decode('utf-16le').rstrip('\x00')
        print(f"  Package chunk at offset {pkg_offset}: type=0x{chunk_type:04x}, name='{pkg_name}'")
    
    # Full ZIP listing
    print(f"\nFull ZIP listing ({len(z.namelist())} entries):")
    for n in sorted(z.namelist()):
        info = z.getinfo(n)
        print(f"  {n} ({info.file_size} bytes)")
