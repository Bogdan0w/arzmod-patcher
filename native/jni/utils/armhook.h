#pragma once

#include "utils/addresses.h"
#include "dobby/include/dobby.h"
#include <set>
#include <sstream>
#include <iomanip>
#include <string>

size_t GetLibrarySize(const char* lib_name);
uintptr_t FindLibrary(const char* library);
void UnFuck(uintptr_t ptr);
void NOP(uintptr_t addr, unsigned int count);
void WriteMemory(uintptr_t dest, uintptr_t src, size_t size);
void ReadMemory(uintptr_t dest, uintptr_t src, size_t size);

void SetUpHook(uintptr_t addr, uintptr_t func, uintptr_t *orig);
void InstallMethodHook(uintptr_t addr, uintptr_t func);
void CodeInject(uintptr_t addr, uintptr_t func, int rgstr);
void InitHookStuff(const char* lib_name);

#ifdef __arm__
    #define bit 32
#elif defined __aarch64__
    #define bit 64
#else
    #error "Unsupported architecture"
#endif


template <size_t N>
std::string patternToHexString(const char (&pattern)[N]) {
    std::ostringstream oss;
    for (size_t i = 0; i < N; ++i) {
        oss << std::hex << std::setw(2) << std::setfill('0')
            << (static_cast<unsigned>(static_cast<unsigned char>(pattern[i])));
    }
    return oss.str();
}

static std::set<std::string> hooked_pattern_hashes;

template <size_t N>
#ifdef __arm__
int PatternHook(const char(&pattern)[N], uintptr_t start, size_t length, uintptr_t func, uintptr_t *orig, const char* tag = nullptr, bool isDobbyUsed = false)
#elif defined __aarch64__
int PatternHook(const char(&pattern)[N], uintptr_t start, size_t length, uintptr_t func, uintptr_t *orig, const char* tag = nullptr, bool isDobbyUsed = true)
#endif
{
    std::string pattern_str = patternToHexString(pattern) + " at " + std::to_string(start);
    if (hooked_pattern_hashes.count(pattern_str)) {
        #ifdef RELEASE_BUILD
            if (tag) LOGE("[%d] Already hooked: %s", bit, tag);
            else LOGE("[%d] Already hooked pattern: UNKNOWN", bit);
        #else
            if (tag) LOGE("[%d] Already hooked: %s at %s", bit, tag, pattern_str.c_str());
            else LOGE("[%d] Already hooked pattern: %s", bit, pattern_str.c_str());
        #endif
        return 1;
    }

    #ifdef RELEASE_BUILD
        if (tag) LOGD("[%d] Try to find pattern for %s", bit, tag);
        else LOGD("[%d] Try to find pattern for UNKNOWN", bit);
    #else
        if (tag) LOGD("[%d] Try to find pattern %s for %s", bit, pattern_str.c_str(), tag);
        else LOGD("[%d] Try to find pattern %s", bit, pattern_str.c_str());
    #endif
    void* func_addr = FindPattern(pattern, start, length);
    if(func_addr) {
        if(isDobbyUsed) {
            DobbyHook(reinterpret_cast<void*>(func_addr), reinterpret_cast<void*>(func), reinterpret_cast<void**>(orig));
        } else {
            SetUpHook(reinterpret_cast<uintptr_t>(func_addr), func, orig);
        }
        
        hooked_pattern_hashes.insert(pattern_str);
        
        if(tag) {
            #ifdef RELEASE_BUILD
                LOGI("[%d] Hooks installed successfully (%s)", bit, tag, );
            #else
                #ifdef __arm__
                    LOGI("[%d] Hooks installed successfully (%s), address: %x (static %x)", bit, tag, (uintptr_t)func_addr, (uintptr_t)func_addr - (uintptr_t)start);
                #elif defined __aarch64__
                    LOGI("[%d] Hooks installed successfully (%s), address: %lx (static %lx)", bit, tag, (uintptr_t)func_addr, (uintptr_t)func_addr - (uintptr_t)start);
                #endif
            #endif
        }
        return (uintptr_t)func_addr;
    }
    else {
        if(tag) {
            LOGE("[%d] Can't find offset from pattern %s (%s)", bit, pattern, tag);
        }
        return 0;
    }
    return (uintptr_t)func_addr;
}