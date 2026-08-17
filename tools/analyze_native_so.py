import os
import sys
from elftools.elf.elffile import ELFFile
from elftools.elf.sections import SymbolTableSection
from capstone import Cs, CS_ARCH_ARM64, CS_MODE_ARM

LIBS_DIR = "native_libs/arm64-v8a"

def analyze_so(filename):
    filepath = os.path.join(LIBS_DIR, filename)
    if not os.path.exists(filepath):
        print(f"File not found: {filepath}")
        return

    print("=" * 80)
    print(f"ANALYZING: {filename}")
    print("=" * 80)

    with open(filepath, 'rb') as f:
        elf = ELFFile(f)
        
        # 1. Basic ELF Info
        print(f"Architecture: {elf['e_machine']} | Class: {elf.elfclass}-bit | Endian: {elf.little_endian and 'Little' or 'Big'}")
        
        # 2. Sections
        sections = [s.name for s in elf.iter_sections()]
        print(f"Sections ({len(sections)}): {', '.join([s for s in sections if s])}")
        
        # 3. Dynamic Symbols (Exports & Imports)
        dynsym = elf.get_section_by_name('.dynsym')
        exported_funcs = []
        imported_funcs = []
        jni_funcs = []
        
        if dynsym and isinstance(dynsym, SymbolTableSection):
            for sym in dynsym.iter_symbols():
                name = sym.name
                if not name:
                    continue
                shndx = sym['st_shndx']
                st_info_type = sym['st_info']['type']
                st_value = sym['st_value']
                
                if shndx == 'SHN_UNDEF':
                    imported_funcs.append(name)
                else:
                    exported_funcs.append((name, st_value, st_info_type))
                    if 'Java_' in name or 'JNI_' in name:
                        jni_funcs.append((name, st_value))
                        
        print(f"\n[+] Exported Functions/Symbols ({len(exported_funcs)}):")
        for name, val, st_type in sorted(exported_funcs, key=lambda x: x[1])[:35]:
            print(f"    0x{val:08x} [{st_type:10}] {name}")
        if len(exported_funcs) > 35:
            print(f"    ... and {len(exported_funcs) - 35} more exports")
                
        print(f"\n[+] JNI Specific Exports ({len(jni_funcs)}):")
        for name, val in jni_funcs:
            print(f"    0x{val:08x} -> {name}")
            
        print(f"\n[+] Key Imported Functions (Libc / System / Hooking APIs) ({len(imported_funcs)} total):")
        interesting_imports = [i for i in imported_funcs if any(k in i.lower() for k in [
            'open', 'stat', 'read', 'write', 'prop', 'dl', 'mprotect', 'ptrace', 
            'syscall', 'fork', 'exec', 'kill', 'pipe', 'socket', 'connect', 'bind', 
            'jni', 'art', 'dalvik', 'hook', 'log'
        ])]
        for imp in sorted(interesting_imports):
            print(f"    - {imp}")

        # 4. Strings Analysis (.rodata & .data)
        print(f"\n[+] Interesting Strings / Indicators in Binary:")
        f.seek(0)
        raw_bytes = f.read()
        
        import re
        all_strings = re.findall(rb'[\x20-\x7e]{4,}', raw_bytes)
        decoded_strings = [s.decode('utf-8', errors='ignore') for s in all_strings]
        
        keywords = [
            'android_id', 'imei', 'imsi', 'mac', 'serial', 'ro.build', 'ro.product',
            'getprop', 'sandbox', 'hook', 'cloner', 'dexprotector', 'magisk', 'su',
            'frida', 'xposed', 'substrate', 'RegisterNatives', 'FindClass', 'GetMethodID'
        ]
        
        found_keywords = {}
        for s in decoded_strings:
            for kw in keywords:
                if kw.lower() in s.lower():
                    found_keywords.setdefault(kw, set()).add(s)
                    
        for kw, str_set in found_keywords.items():
            print(f"    [*] Keyword '{kw}' ({len(str_set)} matches):")
            for sample in list(str_set)[:5]:
                print(f"        \"{sample}\"")
                
        # 5. Quick Disassembly of JNI_OnLoad if available
        jni_onload = next((item for item in exported_funcs if item[0] == 'JNI_OnLoad'), None)
        if jni_onload and elf.get_section_by_name('.text'):
            text_sec = elf.get_section_by_name('.text')
            text_addr = text_sec['sh_addr']
            text_data = text_sec.data()
            func_addr = jni_onload[1]
            func_offset = func_addr - text_addr
            
            if 0 <= func_offset < len(text_data):
                print(f"\n[+] Disassembly of JNI_OnLoad (0x{func_addr:08x}) [First 25 Instructions]:")
                md = Cs(CS_ARCH_ARM64, CS_MODE_ARM)
                code_chunk = text_data[func_offset:func_offset + 100]
                for insn in md.disasm(code_chunk, func_addr):
                    print(f"    0x{insn.address:08x}:  {insn.mnemonic:10} {insn.op_str}")

if __name__ == '__main__':
    target_libs = ['libappcloner.so', 'libsystem.so', 'libdexprotector.so', 'libspake2.so']
    for lib in target_libs:
        analyze_so(lib)
        print("\n\n")
