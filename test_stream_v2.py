import struct, os, hashlib

def compute_top_digest_stream(file_path, cd_offset, cd_size, eocd_pos, file_size):
    md = hashlib.sha256()
    chunk_hashes = bytearray()
    chunk_count = 0
    
    sections = [
        (0, cd_offset),
        (cd_offset, cd_size),
        (eocd_pos, file_size - eocd_pos)
    ]
    
    with open(file_path, 'rb') as f:
        for start_offset, sec_len in sections:
            f.seek(start_offset)
            bytes_left = sec_len
            while bytes_left > 0:
                chunk_len = min(1048576, bytes_left)
                data = f.read(chunk_len)
                prefix = b'\xa5' + struct.pack('<I', chunk_len)
                c_hash = hashlib.sha256(prefix + data).digest()
                chunk_hashes.extend(c_hash)
                chunk_count += 1
                bytes_left -= chunk_len
                
    top_prefix = b'\x5a' + struct.pack('<I', chunk_count)
    return hashlib.sha256(top_prefix + chunk_hashes).digest()

apk_path = 'AppCloner-Studio-v1.0.apk'
file_size = os.path.getsize(apk_path)
with open(apk_path, 'rb') as f:
    f.seek(max(0, file_size - 65557))
    tail = f.read()
    eocd_pos_in_tail = tail.rfind(b'\x50\x4b\x05\x06')
    eocd_pos = max(0, file_size - 65557) + eocd_pos_in_tail
    f.seek(eocd_pos + 12)
    cd_size = struct.unpack('<I', f.read(4))[0]
    cd_offset = struct.unpack('<I', f.read(4))[0]

digest = compute_top_digest_stream(apk_path, cd_offset, cd_size, eocd_pos, file_size)
print(f'Top digest computed by streaming: {digest.hex()}')
