import struct, zipfile

with zipfile.ZipFile('AppCloner-Studio-v1.0.apk', 'r') as z:
    manifest_bytes = z.read('AndroidManifest.xml')

# Parse StringPool
sp_type, sp_size, str_count, style_count, flags, str_start, style_start = struct.unpack('<IIIIIII', manifest_bytes[8:36])
is_utf8 = bool(flags & 0x100)
offsets = list(struct.unpack(f'<{str_count}I', manifest_bytes[36:36 + str_count*4]))
pool_data = manifest_bytes[8 + str_start: 8 + sp_size]

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

# Now iterate XML chunks
idx = 8 + sp_size
while idx < len(manifest_bytes):
    chunk_type, chunk_header_size, chunk_size = struct.unpack('<HHI', manifest_bytes[idx:idx+8])
    if chunk_type == 0x0102: # START_TAG
        line_num, comment_idx, ns_idx, tag_name_idx = struct.unpack('<IIII', manifest_bytes[idx+8:idx+24])
        attr_start, attr_size, attr_count = struct.unpack('<HHH', manifest_bytes[idx+24:idx+30])
        tag_name = strings[tag_name_idx] if tag_name_idx < len(strings) else f'str#{tag_name_idx}'
        print(f'Tag <{tag_name}> (attrs: {attr_count}):')
        
        # Parse attributes
        attr_offset = idx + 8 + 8 + attr_start
        for a in range(attr_count):
            a_off = attr_offset + a * attr_size
            a_ns, a_name, a_raw_val, tv_size, tv_res0, tv_type, tv_data = struct.unpack('<IIIHBB I', manifest_bytes[a_off:a_off+20])
            a_name_str = strings[a_name] if a_name < len(strings) else f'str#{a_name}'
            a_val_str = strings[a_raw_val] if (a_raw_val != 0xFFFFFFFF and a_raw_val < len(strings)) else (strings[tv_data] if (tv_type == 3 and tv_data < len(strings)) else f'type={tv_type}, data={tv_data}')
            print(f'   - {a_name_str} = "{a_val_str}" (raw_val_idx: {a_raw_val}, tv_data: {tv_data})')
            
    idx += chunk_size
