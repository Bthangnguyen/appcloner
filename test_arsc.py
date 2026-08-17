import struct, zipfile

with zipfile.ZipFile('AppCloner-Studio-v1.0.apk', 'r') as z:
    arsc_bytes = bytearray(z.read('resources.arsc'))

def inspect_and_modify_arsc(arsc, old_pkg, new_pkg):
    idx = 0
    modified = False
    while idx < len(arsc) - 264:
        chunk_type = struct.unpack('<H', arsc[idx:idx+2])[0]
        if chunk_type == 0x0200:
            pkg_id = struct.unpack('<I', arsc[idx+8:idx+12])[0]
            pkg_name_utf16 = arsc[idx+12 : idx+12+256]
            pkg_name = pkg_name_utf16.decode('utf-16le').rstrip('\x00')
            print(f'Found Package in resources.arsc: id=0x{pkg_id:x}, name="{pkg_name}"')
            
            if pkg_name == old_pkg:
                new_utf16 = new_pkg.encode('utf-16le')
                new_padded = new_utf16 + b'\x00' * (256 - len(new_utf16))
                arsc[idx+12 : idx+12+256] = new_padded
                print(f'Updated resources.arsc package to: "{new_pkg}"')
                modified = True
        idx += 4
    return modified

inspect_and_modify_arsc(arsc_bytes, 'com.cloner.app', 'com.cloner.app.clone1')
