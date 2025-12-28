#include <iostream>
#include <jni.h>
#include <android/log.h>
#include <dlfcn.h>
#include <cstring>
#include <sys/mman.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <sys/stat.h> 
#include <sys/mman.h>
#include <cstdarg>
#include <exception>
#include <fstream>
#include "addresses.h"
#include "logging.h"
#include <cxxabi.h>

std::string GetFunctionPattern(void* func_addr, size_t size) {
    if (!func_addr || size == 0) {
        return "";
    }
    
    unsigned char* bytes = (unsigned char*)func_addr;
    std::string pattern;
    pattern.reserve(size * 2);
    
    for(size_t i = 0; i < size; i++) {
        char hex[3];
        snprintf(hex, sizeof(hex), "%02X", bytes[i]);
        pattern += hex;
    }
    
    return pattern;
}

void* FindPattern(const std::string& pattern_str, uintptr_t start, size_t length) {
    std::vector<uint8_t> pattern_bytes;
    std::vector<bool> mask;
    
    std::string pattern = pattern_str;
    pattern.erase(std::remove(pattern.begin(), pattern.end(), ' '), pattern.end());
    
    for (size_t i = 0; i < pattern.length(); ) {
        if (i + 1 < pattern.length() && pattern[i] == '?' && pattern[i + 1] == '?') {
            pattern_bytes.push_back(0);
            mask.push_back(false);
            i += 2;
        } else if (i + 1 < pattern.length()) {
            std::string byte_str = pattern.substr(i, 2);
            pattern_bytes.push_back(static_cast<uint8_t>(std::stoi(byte_str, nullptr, 16)));
            mask.push_back(true);
            i += 2;
        } else {
            return nullptr;
        }
    }
    
    const char* memory = static_cast<const char*>((void*)start);
    size_t pattern_len = pattern_bytes.size();
    
    for (size_t i = 0; i <= length - pattern_len; i++) {
        bool found = true;
        for (size_t j = 0; j < pattern_len; j++) {
            if (mask[j] && memory[i + j] != pattern_bytes[j]) {
                found = false;
                break;
            }
        }
        if (found) {
            return const_cast<void*>(reinterpret_cast<const void*>(memory + i));
        }
    }
    
    return nullptr;
}

uintptr_t FindLibrary(const char* library)
{
    char filename[0xFF] = {0},
    buffer[2048] = {0};
    FILE *fp = 0;
    uintptr_t address = 0;

    sprintf( filename, "/proc/%d/maps", getpid() );

    fp = fopen( filename, "rt" );
    if(fp == 0)
    {
        LOGE("ERROR: can't open file %s", filename);
        goto done;
    }

    while(fgets(buffer, sizeof(buffer), fp))
    {
        if( strstr( buffer, library ) )
        {
            address = (uintptr_t)strtoul( buffer, 0, 16 );
            break;
        }
    }

    done:

    if(fp)
      fclose(fp);

    return address;
}

size_t GetLibrarySize(const char* lib_name) {
    char filename[0xFF] = {0};
    char buffer[2048] = {0};
    FILE *fp = nullptr;
    size_t lib_size = 0;
    
    #if defined(__aarch64__)
        uintptr_t min_addr = 0xFFFFFFFFFFFFFFFF;
    #else
        uintptr_t min_addr = 0xFFFFFFFF;
    #endif
    uintptr_t max_addr = 0;

    sprintf(filename, "/proc/%d/maps", getpid());
    
    fp = fopen(filename, "rt");
    if (fp == nullptr) {
        LOGE("ERROR: can't open file %s", filename);
        return 0;
    }

    while (fgets(buffer, sizeof(buffer), fp)) {
        if (strstr(buffer, lib_name)) {
            uintptr_t start_addr, end_addr;
            #if defined(__aarch64__)
                if (sscanf(buffer, "%lx-%lx", &start_addr, &end_addr) == 2) {
            #else
                if (sscanf(buffer, "%x-%x", (unsigned int*)&start_addr, (unsigned int*)&end_addr) == 2) {
            #endif
                if (start_addr < min_addr) min_addr = start_addr;
                if (end_addr > max_addr) max_addr = end_addr;
            } else {
                LOGE("Failed to parse addresses from: %s", buffer);
            }
        }
    }

    fclose(fp);

    #if defined(__aarch64__)
        if (min_addr != 0xFFFFFFFFFFFFFFFF && max_addr != 0) {
    #else
        if (min_addr != 0xFFFFFFFF && max_addr != 0) {
    #endif
        lib_size = max_addr - min_addr;
    } else {
        LOGE("ERROR: library size not found for %s", lib_name);
    }

    return lib_size;
}

LibraryInfo FindLibraryByPrefix(const char* library_prefix)
{
    LibraryInfo result;
    result.address = 0;
    memset(result.name, 0, sizeof(result.name));
    
    char filename[0xFF] = {0};
    char buffer[2048] = {0};
    FILE *fp = 0;
    size_t prefix_len = strlen(library_prefix);

    sprintf(filename, "/proc/%d/maps", getpid());

    fp = fopen(filename, "rt");
    if(fp == 0)
    {
        LOGE("ERROR: can't open file %s", filename);
        goto done;
    }

    while(fgets(buffer, sizeof(buffer), fp))
    {
        char* lib_name = strrchr(buffer, '/');
        if(lib_name)
        {
            lib_name++;
            if(strncmp(lib_name, library_prefix, prefix_len) == 0)
            {
                result.address = (uintptr_t)strtoul(buffer, 0, 16);
                
                char* end = lib_name;
                while (*end && *end >= 32 && *end != ' ') {
                    end++;
                }
                
                if (end > lib_name) {
                    size_t name_len = end - lib_name;
                    strncpy(result.name, lib_name, name_len);
                    result.name[name_len] = '\0';
                } else {
                    LOGE("Invalid library name");
                }
                break;
            }
        }
    }

    done:
    if(fp)
        fclose(fp);

    return result;
}

bool getLibraryFromAddress(void* address, std::string& outPath, uintptr_t* outBase) {
    if (address == nullptr) return false;

    Dl_info info{};
    if (dladdr(address, &info) != 0 && info.dli_fname != nullptr) {
        outPath = info.dli_fname;
        if (outBase) *outBase = reinterpret_cast<uintptr_t>(info.dli_fbase);
        return true;
    }

    std::ifstream maps("/proc/self/maps");
    if (!maps.is_open()) return false;

    const uintptr_t target = reinterpret_cast<uintptr_t>(address);
    std::string line;
    while (std::getline(maps, line)) {
        const char* c = line.c_str();
        char* endPtr = nullptr;
        uintptr_t start = strtoull(c, &endPtr, 16);
        if (endPtr == nullptr || *endPtr != '-') continue;
        uintptr_t end = strtoull(endPtr + 1, &endPtr, 16);
        if (endPtr == nullptr) continue;
        if (target < start || target >= end) continue;

        const char* pathStart = strchr(c, '/');
        if (pathStart != nullptr) {
            outPath.assign(pathStart);
        } else {
            outPath.clear();
        }
        if (outBase) *outBase = start;
        return true;
    }
    return false;
}

std::string getLibraryNameOnly(const std::string& fullPath) {
    if (fullPath.empty()) return std::string();
    size_t pos = fullPath.find_last_of('/');
    return (pos == std::string::npos) ? fullPath : fullPath.substr(pos + 1);
}

void logLibraryForAddress(const char* tag, void* address) {
    std::string path;
    uintptr_t base = 0;
    if (getLibraryFromAddress(address, path, &base)) {
        uintptr_t off = reinterpret_cast<uintptr_t>(address) - base;
        Dl_info info{};
        const char* rawName = nullptr;
        const char* prettyName = nullptr;
        char* demangled = nullptr;
        int status = 0;
        size_t demangledSize = 0;

        if (dladdr(address, &info) != 0 && info.dli_sname != nullptr) {
            rawName = info.dli_sname;
            demangled = abi::__cxa_demangle(rawName, nullptr, &demangledSize, &status);
            if (status == 0 && demangled != nullptr) {
                prettyName = demangled;
            } else {
                prettyName = rawName;
            }

            uintptr_t symBase = reinterpret_cast<uintptr_t>(info.dli_saddr);
            uintptr_t symOff = reinterpret_cast<uintptr_t>(address) - symBase;
            LOGI("%s: %p -> %s (base=%p, off=0x%zx), sym=%s + 0x%zx", tag, address, path.c_str(), (void*)base, (size_t)off, prettyName, (size_t)symOff);
        } else {
            LOGI("%s: %p -> %s (base=%p, off=0x%zx)", tag, address, path.c_str(), (void*)base, (size_t)off);
        }

        if (demangled) {
            free(demangled);
        }
    } else {
        LOGI("%s: %p -> <unknown>", tag, address);
    }
}