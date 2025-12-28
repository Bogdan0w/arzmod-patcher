#pragma once

#include <cstdint>
#include <string>
#include <vector>
#include <algorithm>

struct LibraryInfo {
    uintptr_t address;
    char name[256];
};




size_t GetLibrarySize(const char* lib_name);
uintptr_t FindLibrary(const char* library);
LibraryInfo FindLibraryByPrefix(const char* library_prefix);

std::string GetFunctionPattern(void* func_addr, size_t size);
bool getLibraryFromAddress(void* address, std::string& outPath, uintptr_t* outBase);
std::string getLibraryNameOnly(const std::string& fullPath);
void logLibraryForAddress(const char* tag, void* address);
void* FindPattern(const std::string& pattern_str, uintptr_t start, size_t length);