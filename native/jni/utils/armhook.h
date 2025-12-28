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
    for (size_t i = 0; i < N - 1; ++i) {
        oss << std::uppercase << std::hex << std::setw(2) << std::setfill('0')
            << (static_cast<unsigned>(static_cast<unsigned char>(pattern[i])));
        if (i != N - 2)
            oss << ' ';
    }
    return oss.str();
}

static std::set<std::string> hooked_pattern_hashes;

#ifdef __arm__
int PatternHook(const std::string& pattern, uintptr_t start, size_t length, uintptr_t func, uintptr_t *orig, const char* tag = nullptr, bool isDobbyUsed = false);
#elif defined __aarch64__
int PatternHook(const std::string& pattern, uintptr_t start, size_t length, uintptr_t func, uintptr_t *orig, const char* tag = nullptr, bool isDobbyUsed = true);
#endif