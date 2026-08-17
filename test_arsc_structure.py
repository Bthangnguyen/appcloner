import struct, zipfile

with zipfile.ZipFile('AppCloner-Studio-v1.0.apk', 'r') as z:
    arsc = bytearray(z.read('resources.arsc'))

pkg_count = struct.unpack('<I', arsc[8:12])[0]
print(f'Package count: {pkg_count}')

sp_type, sp_header_size, sp_size = struct.unpack('<HHI', arsc[12:20])
print(f'StringPool: type=0x{sp_type:04x}, size={sp_size}')

pkg_offset = 12 + sp_size
p_type, p_header_size, p_size = struct.unpack('<HHI', arsc[pkg_offset:pkg_offset+8])
pkg_id = struct.unpack('<I', arsc[pkg_offset+8:pkg_offset+12])[0]
pkg_name = arsc[pkg_offset+12:pkg_offset+12+256].decode('utf-16le').rstrip('\x00')
print(f'Package chunk at {pkg_offset}: type=0x{p_type:04x}, id=0x{pkg_id:x}, name="{pkg_name}"')
