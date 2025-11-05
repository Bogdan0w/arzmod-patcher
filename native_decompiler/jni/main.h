/*
Requirements:
  Android NDK
  C++17
  Android API
*/

#include <cmath>
#include <cstdint>
#include <string>
#include <unordered_map>
#include <vector>
#include <limits>
#include <algorithm>
#include <dirent.h>
#include <sys/stat.h>
#include <unistd.h>
#include <android/log.h>
#include <jni.h>

#define DEBUG_INFO __FUNCTION__, __FILE__, __LINE__
#define LOG_TAG "LuaJITDecompiler"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

constexpr char PROGRAM_NAME[] = "LuaJIT Decompiler v2";
constexpr uint64_t DOUBLE_SIGN = 0x8000000000000000;
constexpr uint64_t DOUBLE_EXPONENT = 0x7FF0000000000000;
constexpr uint64_t DOUBLE_FRACTION = 0x000FFFFFFFFFFFFF;
constexpr uint64_t DOUBLE_SPECIAL = DOUBLE_EXPONENT;
constexpr uint64_t DOUBLE_NEGATIVE_ZERO = DOUBLE_SIGN;

typedef void (*ProgressCallback)(const char* message);
typedef void (*ErrorCallback)(const char* message);

extern ProgressCallback g_progressCallback;
extern ErrorCallback g_errorCallback;

void print(const std::string& message);
void print_progress_bar(const double& progress = 0, const double& total = 100);
void erase_progress_bar();
void assert(const bool& assertion, const std::string& message, const std::string& filePath, const std::string& function, const std::string& source, const uint32_t& line);
std::string byte_to_string(const uint8_t& byte);

std::string get_file_extension(const std::string& filename);
std::string get_filename_without_extension(const std::string& filename);
bool create_directory(const std::string& path);
bool file_exists(const std::string& path);
bool is_directory(const std::string& path);

class Bytecode;
class Ast;
class Lua;

#include "bytecode\unprot.h"
#include "bytecode\bytecode.h"
#include "ast\ast.h"
#include "lua\lua.h"
