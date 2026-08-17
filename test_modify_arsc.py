import struct, zipfile

with zipfile.ZipFile('AppCloner-Studio-v1.0.apk', 'r') as z:
    arsc = bytearray(z.read('resources.arsc'))

def modify_arsc(arsc, old_pkg, new_pkg):
    root_header_size = struct.unpack('<H', arsc[2:4])[0]
    sp_size = struct.unpack('<I', arsc[root_header_size+4:root_header_size+8])[0]
    pkg_offset = root_header_size + sp_size
    
    while pkg_offset < len(arsc) - 268:
        chunk_type, chunk_header_size, chunk_size = struct.unpack('<HHI', arsc[pkg_offset:pkg_offset+8])
        if chunk_type == 0x0200:
            pkg_name = arsc[pkg_offset+12:pkg_offset+12+256].decode('utf-16le').rstrip('\x00')
            print(f'Found package: "{pkg_name}" at offset {pkg_offset}')
            if pkg_name == old_pkg:
                new_utf16 = new_pkg.encode('utf-16le')
                new_padded = new_utf16 + b'\x00' * (256 - len(new_utf16))
                arsc[pkg_offset+12 : pkg_offset+12+256] = new_padded
                print(f'Successfully updated package to: "{new_pkg}"')
        if chunk_size <= 0: break
        pkg_offset += chunk_size
    return arsc

mod_arsc = modify_arsc(arsc, 'com.cloner.app', 'com.cloner.app.clone1')
