import os, sys, struct
from elftools.elf.elffile import ELFFile
from elftools.elf.relocation import RelocationSection

def inspect_relocations_and_strings(so_name):
    path = os.path.join("native_libs/arm64-v8a", so_name)
    print("=" * 80)
    print(f"DEEP RELOCATION & STRING DUMP: {so_name}")
    print("=" * 80)
    
    with open(path, 'rb') as f:
        elf = ELFFile(f)
        dynsym = elf.get_section_by_name('.dynsym')
        dynsym_symbols = [s.name for s in dynsym.iter_symbols()] if dynsym else []
        
        for section in elf.iter_sections():
            if isinstance(section, RelocationSection):
                print(f"\n[+] Relocation Section: {section.name} ({section.num_relocations()} entries)")
                for i, reloc in enumerate(section.iter_relocations()):
                    sym_idx = reloc['r_info_sym']
                    sym_name = dynsym_symbols[sym_idx] if sym_idx < len(dynsym_symbols) else ''
                    r_type = reloc['r_info_type']
                    r_offset = reloc['r_offset']
                    if sym_name or i < 20:
                        if sym_name:
                            print(f"    0x{r_offset:08x}: type={r_type:4} sym={sym_name}")

inspect_relocations_and_strings('libappcloner.so')
inspect_relocations_and_strings('libdexprotector.so')
