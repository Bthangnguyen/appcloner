import struct, zipfile, sys

def analyze_and_modify_manifest(manifest_bytes, orig_pkg, new_pkg, auth_suffix=".clone1"):
    # 1. Parse StringPool
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

    # 2. Parse XML Tree to find exact indices for package, authorities, permissions, components
    package_str_indices = set()
    authority_str_indices = set()
    permission_str_indices = set()
    uses_permission_str_indices = set()
    component_name_str_indices = set()

    idx = 8 + sp_size
    while idx < len(manifest_bytes):
        chunk_type, chunk_header_size, chunk_size = struct.unpack('<HHI', manifest_bytes[idx:idx+8])
        if chunk_type == 0x0102: # START_TAG
            line_num, comment_idx, ns_idx, tag_name_idx = struct.unpack('<IIII', manifest_bytes[idx+8:idx+24])
            attr_start, attr_size, attr_count = struct.unpack('<HHH', manifest_bytes[idx+24:idx+30])
            tag_name = strings[tag_name_idx] if tag_name_idx < len(strings) else ''
            
            attr_offset = idx + 8 + 8 + attr_start
            for a in range(attr_count):
                a_off = attr_offset + a * attr_size
                a_ns, a_name, a_raw_val, tv_size, tv_res0, tv_type, tv_data = struct.unpack('<IIIHBB I', manifest_bytes[a_off:a_off+20])
                val_idx = a_raw_val if a_raw_val != 0xFFFFFFFF else (tv_data if tv_type == 3 else None)
                if val_idx is None or val_idx >= len(strings):
                    continue
                    
                attr_name = strings[a_name] if a_name < len(strings) else ''
                
                if tag_name == 'manifest' and attr_name == 'package':
                    package_str_indices.add(val_idx)
                elif tag_name == 'provider' and attr_name == 'authorities':
                    authority_str_indices.add(val_idx)
                elif tag_name == 'permission' and attr_name == 'name':
                    permission_str_indices.add(val_idx)
                elif tag_name == 'uses-permission' and attr_name == 'name':
                    uses_permission_str_indices.add(val_idx)
                elif tag_name in ('activity', 'service', 'receiver', 'provider', 'application') and attr_name == 'name':
                    component_name_str_indices.add(val_idx)

        idx += chunk_size

    # 3. Modify Strings with precise semantics
    modified_strings = list(strings)

    # 3a. Manifest package
    for pi in package_str_indices:
        modified_strings[pi] = new_pkg

    # 3b. Provider authorities
    for ai in authority_str_indices:
        old_auth = strings[ai]
        if orig_pkg in old_auth:
            modified_strings[ai] = old_auth.replace(orig_pkg, new_pkg)
        elif not old_auth.endswith(auth_suffix):
            modified_strings[ai] = f"{old_auth}{auth_suffix}"

    # 3c. Custom Permissions declared
    custom_perms = set()
    for permi in permission_str_indices:
        old_perm = strings[permi]
        custom_perms.add(old_perm)
        if orig_pkg in old_perm:
            modified_strings[permi] = old_perm.replace(orig_pkg, new_pkg)
        elif not old_perm.endswith(auth_suffix):
            modified_strings[permi] = f"{old_perm}{auth_suffix}"

    # 3d. Uses-permission matching custom permissions
    for upi in uses_permission_str_indices:
        old_up = strings[upi]
        if old_up in custom_perms:
            if orig_pkg in old_up:
                modified_strings[upi] = old_up.replace(orig_pkg, new_pkg)
            elif not old_up.endswith(auth_suffix):
                modified_strings[upi] = f"{old_up}{auth_suffix}"

    # 3e. Relative Component Names (e.g. ".MainActivity" -> "com.orig.MainActivity")
    for ci in component_name_str_indices:
        old_cname = strings[ci]
        if old_cname.startswith('.'):
            modified_strings[ci] = f"{orig_pkg}{old_cname}"

    print(f"Package indices: {package_str_indices} -> {new_pkg}")
    print(f"Authorities modified: {[modified_strings[i] for i in authority_str_indices]}")
    print(f"Permissions modified: {[modified_strings[i] for i in permission_str_indices]}")
    print(f"Components expanded: {[modified_strings[i] for i in component_name_str_indices if strings[i].startswith('.')]}")

with zipfile.ZipFile('AppCloner-Studio-v1.0.apk', 'r') as z:
    manifest_bytes = z.read('AndroidManifest.xml')

analyze_and_modify_manifest(manifest_bytes, 'com.cloner.app', 'com.cloner.app.clone1')
