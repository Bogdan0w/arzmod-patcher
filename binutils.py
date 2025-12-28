import lief
import struct
from pathlib import Path


def find_string_in_section(binary, target_string):
    sections_to_search = ['.rodata', '.data.rel.ro', '.data', '.text']
    
    for section in binary.sections:
        if any(s in section.name for s in sections_to_search):
            content = bytes(section.content)
            offset = content.find(target_string)
            if offset != -1:
                string_va = section.virtual_address + offset
                return string_va
    return None


def find_references_arm64(binary, string_va):
    references = []
    text_section = next((s for s in binary.sections if s.name == '.text'), None)
    if not text_section:
        return references
    
    content = bytes(text_section.content)
    base_va = text_section.virtual_address
    
    for i in range(0, len(content) - 4, 4):
        instr_va = base_va + i
        instr = struct.unpack('<I', content[i:i+4])[0]
        
        # ADRP
        if (instr & 0x9F000000) == 0x90000000:
            rd = instr & 0x1F
            immlo = (instr >> 29) & 0x3
            immhi = (instr >> 5) & 0x7FFFF
            imm = (immhi << 2) | immlo
            if imm & 0x100000:
                imm |= ~0x1FFFFF
            
            pc_page = instr_va & ~0xFFF
            target_page = (pc_page + (imm << 12)) & 0xFFFFFFFFFFFFFFFF
            
            if i + 4 < len(content) - 4:
                next_instr = struct.unpack('<I', content[i+4:i+8])[0]
                if (next_instr & 0xFFC00000) == 0x91000000:
                    next_rd = next_instr & 0x1F
                    next_rn = (next_instr >> 5) & 0x1F
                    if next_rd == rd and next_rn == rd:
                        add_imm = (next_instr >> 10) & 0xFFF
                        if target_page + add_imm == string_va:
                            references.append(instr_va)
        
        # ADR
        elif (instr & 0x9F000000) == 0x10000000:
            immlo = (instr >> 29) & 0x3
            immhi = (instr >> 5) & 0x7FFFF
            imm = (immhi << 2) | immlo
            if imm & 0x100000:
                imm |= ~0x1FFFFF
            target_address = (instr_va + imm) & 0xFFFFFFFFFFFFFFFF
            if target_address == string_va:
                references.append(instr_va)

    return references


def find_references_arm32(binary, string_va):
    references = []
    text_section = next((s for s in binary.sections if s.name == '.text'), None)
    if not text_section:
        return references
    
    content = bytes(text_section.content)
    base_va = text_section.virtual_address
    
    for i in range(0, len(content) - 4, 2):
        instr_va = base_va + i
        
        if i + 4 > len(content):
            continue
            
        ldr_instr = struct.unpack('<H', content[i:i+2])[0]
        
        # LDR Rt, [PC, #imm] (16-bit Thumb)
        if (ldr_instr & 0xF800) == 0x4800:
            ldr_rt = (ldr_instr >> 8) & 0x7
            ldr_imm8 = ldr_instr & 0xFF
            ldr_pc = ((instr_va + 4) & ~0x3)
            ldr_target = ldr_pc + (ldr_imm8 << 2)
            
            # ADD Rd
            for offset in [2, 4, 6, 8]:
                if i + offset + 2 > len(content):
                    break
                    
                add_instr = struct.unpack('<H', content[i+offset:i+offset+2])[0]
                
                # ADD Rd, PC
                if (add_instr & 0xFF78) == 0x4478:
                    add_rd = ((add_instr >> 7) & 0x1) << 3 | (add_instr & 0x7)
                    
                    if ldr_rt == (add_rd & 0x7):
                        if base_va <= ldr_target < base_va + len(content):
                            pool_offset = ldr_target - base_va
                            if pool_offset + 4 <= len(content):
                                pool_value = struct.unpack('<I', content[pool_offset:pool_offset+4])[0]
                                pool_signed = struct.unpack('<i', struct.pack('<I', pool_value))[0]
                                
                                add_va = instr_va + offset
                                add_pc = add_va + 4
                                final = (add_pc + pool_signed) & 0xFFFFFFFF
 
                                if final == string_va:
                                    references.append(instr_va)
                                    break
        
        # MOVW+MOVT (32-bit Thumb2)
        if i + 8 <= len(content):
            instr = struct.unpack('<I', content[i:i+4])[0]
            
            if (instr & 0xFBF08000) == 0xF2400000:
                rd = (instr >> 8) & 0xF
                imm16_low = ((instr >> 16) & 0xF) << 12 | ((instr >> 26) & 0x1) << 11 | \
                           ((instr >> 12) & 0x7) << 8 | (instr & 0xFF)
                
                next_instr = struct.unpack('<I', content[i+4:i+8])[0]
                if (next_instr & 0xFBF08000) == 0xF2C00000 and ((next_instr >> 8) & 0xF) == rd:
                    imm16_high = ((next_instr >> 16) & 0xF) << 12 | ((next_instr >> 26) & 0x1) << 11 | \
                                ((next_instr >> 12) & 0x7) << 8 | (next_instr & 0xFF)
                    full_addr = (imm16_high << 16) | imm16_low
                    if full_addr == string_va:
                        references.append(instr_va)
    return references


def find_function_start_arm64(binary, ref_address):
    text_section = next((s for s in binary.sections if s.name == '.text'), None)
    if not text_section:
        return None
    
    content = bytes(text_section.content)
    base_va = text_section.virtual_address
    offset_in_section = ref_address - base_va
    
    if offset_in_section < 0 or offset_in_section >= len(content):
        return None
    
    for i in range(offset_in_section, max(0, offset_in_section - 0x10000), -4):
        if i + 4 > len(content):
            continue
        instr = struct.unpack('<I', content[i:i+4])[0]
        instr_va = base_va + i
        
        # STP X29, X30, [SP, ...] -
        if (instr & 0xFFC003E0) == 0xA9BF03E0:
            rt = instr & 0x1F
            rt2 = (instr >> 10) & 0x1F
            if rt == 29 and rt2 == 30:
                return instr_va
        
        # STP X27, X26, [SP, #-0x70]
        if instr == 0xA9BA7BFD:
            return instr_va
        
        # SUB SP, SP, #imm + STP
        if (instr & 0xFF8003FF) == 0xD10003FF:
            if i + 4 < len(content):
                next_instr = struct.unpack('<I', content[i+4:i+8])[0]
                if (next_instr & 0xFFC003E0) == 0xA90003E0:
                    return instr_va
    
    return (ref_address & ~0xF)


def find_function_start_arm32(binary, ref_address, log=print):
    text_section = next((s for s in binary.sections if s.name == '.text'), None)
    if not text_section:
        return None

    content = bytes(text_section.content)
    base_va = text_section.virtual_address
    offset_in_section = ref_address - base_va

    if offset_in_section < 0 or offset_in_section >= len(content):
        return None

    start_scan = max(0, offset_in_section - 0x10000)
    for i in range(offset_in_section, start_scan, -2):
        if i + 2 > len(content):
            continue
        instr16 = struct.unpack('<H', content[i:i+2])[0]
        instr_va = base_va + i

        # Thumb16 PUSH {LR}  PUSH {R4-R7, LR}
        if (instr16 & 0xFF00) == 0xB500:  # PUSH {regs, LR}
            return instr_va

        # Thumb32 PUSH {R4-R11, LR} (upper halfword)
        if i + 4 <= len(content):
            instr32 = struct.unpack('<I', content[i:i+4])[0]
            if (instr32 & 0xFFFFF800) == 0xE92D4000:  # STMFD/Push LR R4-R11
                return instr_va

        # ARM mode PUSH {R4-R11, LR}
        if i % 4 == 0:  # ARM
            instr_arm = struct.unpack('<I', content[i:i+4])[0]
            if (instr_arm & 0xFFFF0000) == 0xE92D0000:
                reg_list = instr_arm & 0xFFFF
                if reg_list & (1 << 14):  # LR
                    return instr_va

            # ARM PUSH {LR} LR
            if (instr_arm & 0xFFFF0000) == 0xE92D0000 and (instr_arm & 0x4000):
                return instr_va

    return ref_address & ~0x3


def find_function_by_containing_string(libpath, target_string):
    if isinstance(target_string, str):
        target_string = target_string.encode()
    
    try:
        binary = lief.parse(str(libpath))
        if not binary:
            return None
    
        is_arm64 = binary.header.machine_type == lief.ELF.ARCH.AARCH64
        
        string_va = find_string_in_section(binary, target_string)
        if not string_va:
            return None
        
        if is_arm64:
            references = find_references_arm64(binary, string_va)
            find_func = find_function_start_arm64
        else:
            references = find_references_arm32(binary, string_va)
            find_func = find_function_start_arm32
        
        if not references:
            return None
        
        for ref in references:
            func_addr = find_func(binary, ref)
            if func_addr:
                return func_addr
        
        return None
        
    except Exception as e:
        return None


def get_bytes_from_address(libpath, address, num_bytes=64):
    binary = lief.parse(str(libpath))
    if not binary:
        return None

    for section in binary.sections:
        section_start = section.virtual_address
        section_end = section_start + section.size
        
        if section_start <= address < section_end:
            offset = address - section_start
            if offset + num_bytes <= section.size:
                bytes_data = bytes(section.content[offset:offset + num_bytes])
                return ''.join(f'\\x{b:02X}' for b in bytes_data)
    
    return None

