#include "main.h"

struct Error {
	const std::string message;
	const std::string filePath;
	const std::string function;
	const std::string source;
	const std::string line;
};

ProgressCallback g_progressCallback = nullptr;
ErrorCallback g_errorCallback = nullptr;

static JavaVM* g_jvm = nullptr;
static jobject g_progressCallbackObj = nullptr;
static jobject g_errorCallbackObj = nullptr;
static jobject g_progressUpdateCallbackObj = nullptr;
static jmethodID g_progressMethodID = nullptr;
static jmethodID g_errorMethodID = nullptr;
static jmethodID g_progressUpdateMethodID = nullptr;

static bool isProgressBarActive = false;
static uint32_t filesSkipped = 0;

static struct {
	bool showHelp = false;
	bool silentAssertions = false;
	bool forceOverwrite = false;
	bool ignoreDebugInfo = false;
	bool minimizeDiffs = false;
	bool unrestrictedAscii = false;
	std::string inputPath;
	std::string outputPath;
	std::string extensionFilter;
} arguments;

struct Directory {
	const std::string path;
	std::vector<Directory> folders;
	std::vector<std::string> files;
};

static std::string string_to_lowercase(const std::string& string) {
	std::string lowercaseString = string;

	for (uint32_t i = lowercaseString.size(); i--;) {
		if (lowercaseString[i] < 'A' || lowercaseString[i] > 'Z') continue;
		lowercaseString[i] += 'a' - 'A';
	}

	return lowercaseString;
}

static void find_files_recursively(Directory& directory) {
	std::string fullPath = arguments.inputPath + directory.path;
	DIR* dir = opendir(fullPath.c_str());
	if (!dir) return;

	struct dirent* entry;
	while ((entry = readdir(dir)) != nullptr) {
		if (entry->d_name[0] == '.') continue;
		
		std::string entryPath = fullPath + "/" + entry->d_name;
		struct stat statbuf;
		if (stat(entryPath.c_str(), &statbuf) != 0) continue;
		
		if (S_ISDIR(statbuf.st_mode)) {
			directory.folders.emplace_back(Directory{ .path = directory.path + entry->d_name + "/" });
			find_files_recursively(directory.folders.back());
			if (!directory.folders.back().files.size() && !directory.folders.back().folders.size()) directory.folders.pop_back();
		} else if (S_ISREG(statbuf.st_mode)) {
			if (!arguments.extensionFilter.size() || arguments.extensionFilter == string_to_lowercase(get_file_extension(entry->d_name))) {
				directory.files.emplace_back(entry->d_name);
			}
		}
	}
	closedir(dir);
}

static bool decompile_files_recursively(const Directory& directory) {
	create_directory(arguments.outputPath + directory.path);
	std::string outputFile;

	for (uint32_t i = 0; i < directory.files.size(); i++) {
		outputFile = get_filename_without_extension(directory.files[i]) + ".lua";

		Unprot unprot(arguments.inputPath + directory.path + directory.files[i]);
		Bytecode bytecode(arguments.inputPath + directory.path + directory.files[i]);
		Ast ast(bytecode, arguments.ignoreDebugInfo, arguments.minimizeDiffs);
		Lua lua(bytecode, ast, arguments.outputPath + directory.path + outputFile, arguments.forceOverwrite, arguments.minimizeDiffs, arguments.unrestrictedAscii);

		try {
			print("--------------------\nInput file: " + bytecode.filePath);
			print("Unprotecting file...");
			unprot();
			print("Reading bytecode...");
			bytecode();
			print("Building ast...");
			ast();
			print("Writing lua source...");
			lua();
			print("Output file: " + lua.filePath);
			
			print_progress_bar(i + 1, directory.files.size());
			
			if (arguments.forceOverwrite) {
				std::string originalFile = arguments.inputPath + directory.path + directory.files[i];
				if (remove(originalFile.c_str()) == 0) {
					print("Original file removed: " + originalFile);
				}
			}
		} catch (const Error& error) {
			erase_progress_bar();

			if (arguments.silentAssertions) {
				filesSkipped++;
				continue;
			}

			if (g_jvm && g_errorCallbackObj && g_errorMethodID) {
				JNIEnv* env;
				JavaVMAttachArgs attachArgs;
				attachArgs.version = JNI_VERSION_1_6;
				attachArgs.name = "LuaJITDecompiler";
				attachArgs.group = nullptr;
				
				if (g_jvm->AttachCurrentThread(&env, &attachArgs) == JNI_OK) {
					std::string errorMsg = "Error running " + error.function + "\nSource: " + error.source + ":" + error.line + "\n\nFile: " + error.filePath + "\n\n" + error.message;
					jstring jErrorMsg = env->NewStringUTF(errorMsg.c_str());
					env->CallIntMethod(g_errorCallbackObj, g_errorMethodID, jErrorMsg);
					env->DeleteLocalRef(jErrorMsg);
				}
			} else if (g_errorCallback) {
				std::string errorMsg = "Error running " + error.function + "\nSource: " + error.source + ":" + error.line + "\n\nFile: " + error.filePath + "\n\n" + error.message;
				g_errorCallback(errorMsg.c_str());
			} else {
				print("Error: " + error.message);
				filesSkipped++;
			}
		} catch (...) {
			if (g_jvm && g_errorCallbackObj && g_errorMethodID) {
				JNIEnv* env;
				JavaVMAttachArgs attachArgs;
				attachArgs.version = JNI_VERSION_1_6;
				attachArgs.name = "LuaJITDecompiler";
				attachArgs.group = nullptr;
				
				if (g_jvm->AttachCurrentThread(&env, &attachArgs) == JNI_OK) {
					std::string errorMsg = "Unknown exception\n\nFile: " + bytecode.filePath;
					jstring jErrorMsg = env->NewStringUTF(errorMsg.c_str());
					env->CallVoidMethod(g_errorCallbackObj, g_errorMethodID, jErrorMsg);
					env->DeleteLocalRef(jErrorMsg);
				}
			} else if (g_errorCallback) {
				g_errorCallback(("Unknown exception\n\nFile: " + bytecode.filePath).c_str());
			}
			throw;
		}
	}

	for (uint32_t i = 0; i < directory.folders.size(); i++) {
		if (!decompile_files_recursively(directory.folders[i])) return false;
	}

	return true;
}


extern "C" {

JNIEXPORT void JNICALL Java_com_arzmod_radare_LuaJITDecompiler_setNativeCallbacks(
    JNIEnv* env, jclass clazz, 
    jobject progressCallback, jobject errorCallback, jobject progressUpdateCallback) {
    
    env->GetJavaVM(&g_jvm);
    
    if (g_progressCallbackObj) {
        env->DeleteGlobalRef(g_progressCallbackObj);
        g_progressCallbackObj = nullptr;
    }
    if (g_errorCallbackObj) {
        env->DeleteGlobalRef(g_errorCallbackObj);
        g_errorCallbackObj = nullptr;
    }
    if (g_progressUpdateCallbackObj) {
        env->DeleteGlobalRef(g_progressUpdateCallbackObj);
        g_progressUpdateCallbackObj = nullptr;
    }
    
    if (progressCallback) {
        g_progressCallbackObj = env->NewGlobalRef(progressCallback);
        jclass progressClass = env->GetObjectClass(progressCallback);
        g_progressMethodID = env->GetMethodID(progressClass, "onProgress", "(Ljava/lang/String;)V");
        env->DeleteLocalRef(progressClass);
    }
    
    if (progressUpdateCallback) {
        g_progressUpdateCallbackObj = env->NewGlobalRef(progressUpdateCallback);
        jclass progressUpdateClass = env->GetObjectClass(progressUpdateCallback);
        g_progressUpdateMethodID = env->GetMethodID(progressUpdateClass, "onProgressUpdate", "(III)V");
        env->DeleteLocalRef(progressUpdateClass);
    }
    
    if (errorCallback) {
        g_errorCallbackObj = env->NewGlobalRef(errorCallback);
        jclass errorClass = env->GetObjectClass(errorCallback);
        g_errorMethodID = env->GetMethodID(errorClass, "onError", "(Ljava/lang/String;)V");
        env->DeleteLocalRef(errorClass);
    }

}

JNIEXPORT jint JNICALL Java_com_arzmod_radare_LuaJITDecompiler_decompileFiles(
    JNIEnv* env, jclass clazz, 
    jstring inputPath, jstring outputPath, 
    jboolean forceOverwrite, jboolean ignoreDebugInfo, 
    jboolean minimizeDiffs, jboolean unrestrictedAscii, 
    jboolean silentAssertions, jstring extensionFilter) {
    
    const char* inputPathStr = env->GetStringUTFChars(inputPath, nullptr);
    const char* outputPathStr = outputPath ? env->GetStringUTFChars(outputPath, nullptr) : nullptr;
    const char* extensionFilterStr = extensionFilter ? env->GetStringUTFChars(extensionFilter, nullptr) : nullptr;
    
    arguments.inputPath = inputPathStr;
    arguments.outputPath = outputPathStr ? outputPathStr : "";
    arguments.forceOverwrite = forceOverwrite;
    arguments.ignoreDebugInfo = ignoreDebugInfo;
    arguments.minimizeDiffs = minimizeDiffs;
    arguments.unrestrictedAscii = unrestrictedAscii;
    arguments.silentAssertions = silentAssertions;
    arguments.extensionFilter = extensionFilterStr ? extensionFilterStr : "";
    
    env->ReleaseStringUTFChars(inputPath, inputPathStr);
    if (outputPathStr) env->ReleaseStringUTFChars(outputPath, outputPathStr);
    if (extensionFilterStr) env->ReleaseStringUTFChars(extensionFilter, extensionFilterStr);
    
    print(std::string(PROGRAM_NAME) + "\nCompiled on " + __DATE__);
    
    if (arguments.extensionFilter.size()) {
        if (arguments.extensionFilter.front() != '.') arguments.extensionFilter.insert(arguments.extensionFilter.begin(), '.');
        arguments.extensionFilter = string_to_lowercase(arguments.extensionFilter);
    }
    
    if (!file_exists(arguments.inputPath)) {
        print("Failed to open input path: " + arguments.inputPath);
        return -1;
    }
    
    Directory root;
    
    if (is_directory(arguments.inputPath)) {
        if (arguments.inputPath.back() != '/') {
            arguments.inputPath += '/';
        }
        
        find_files_recursively(root);
        
        if (!root.files.size() && !root.folders.size()) {
            print("No files " + (arguments.extensionFilter.size() ? "with extension " + arguments.extensionFilter + " " : "") + "found in path: " + arguments.inputPath);
            return -1;
        }
    } else {
        size_t lastSlash = arguments.inputPath.find_last_of('/');
        if (lastSlash != std::string::npos) {
            root.files.emplace_back(arguments.inputPath.substr(lastSlash + 1));
            arguments.inputPath = arguments.inputPath.substr(0, lastSlash + 1);
        } else {
            root.files.emplace_back(arguments.inputPath);
            arguments.inputPath = "./";
        }
    }
    
    if (arguments.outputPath.empty()) {
        arguments.outputPath = arguments.inputPath;
    } else {
        if (arguments.outputPath.back() != '/') {
            arguments.outputPath += '/';
        }
    }
    
    try {
        if (!decompile_files_recursively(root)) {
            print("--------------------\nAborted!");
            return -1;
        }
    } catch (...) {
        return -1;
    }
    
    print("--------------------\n" + (filesSkipped ? "Failed to decompile " + std::to_string(filesSkipped) + " file" + (filesSkipped > 1 ? "s" : "") + ".\n" : "") + "Done!");
    return 0;
}


static bool isFileHaveBC(const std::string& path) {
	FILE* f = fopen(path.c_str(), "rb");
	if (!f) return false;
	uint8_t hdr[5];
	size_t r = fread(hdr, 1, 5, f);
	fclose(f);
	if (r < 5) return false;
	return hdr[0] == 0x1B && hdr[1] == 0x4C && hdr[2] == 0x4A;
}

JNIEXPORT jboolean JNICALL Java_com_arzmod_radare_LuaJITDecompiler_isFileHaveBC(
	JNIEnv* env, jclass clazz, jstring jpath) {
	const char* cpath = env->GetStringUTFChars(jpath, nullptr);
	bool ok = isFileHaveBC(cpath);
	env->ReleaseStringUTFChars(jpath, cpath);
	return ok ? JNI_TRUE : JNI_FALSE;
}

}

void print(const std::string& message) {	
	if (g_jvm && g_progressCallbackObj && g_progressMethodID) {
		JNIEnv* env;
		JavaVMAttachArgs attachArgs;
		attachArgs.version = JNI_VERSION_1_6;
		attachArgs.name = "LuaJITDecompiler";
		attachArgs.group = nullptr;
		
		if (g_jvm->AttachCurrentThread(&env, &attachArgs) == JNI_OK) {
			jstring jMessage = env->NewStringUTF(message.c_str());
			env->CallVoidMethod(g_progressCallbackObj, g_progressMethodID, jMessage);
			env->DeleteLocalRef(jMessage);
		}
	}
	
	if (g_progressCallback) {
		g_progressCallback(message.c_str());
	}
}

void print_progress_bar(const double& progress, const double& total) {
	int percentage = (int)((progress / total) * 100);
	int current = (int)progress;
	int totalInt = (int)total;
	
	if (g_jvm && g_progressUpdateCallbackObj && g_progressUpdateMethodID) {
		JNIEnv* env;
		JavaVMAttachArgs attachArgs;
		attachArgs.version = JNI_VERSION_1_6;
		attachArgs.name = "LuaJITDecompiler";
		attachArgs.group = nullptr;
		
		if (g_jvm->AttachCurrentThread(&env, &attachArgs) == JNI_OK) {
			env->CallVoidMethod(g_progressUpdateCallbackObj, g_progressUpdateMethodID, 
			                   (jint)percentage, (jint)current, (jint)totalInt);
		}
	}
	
	isProgressBarActive = true;
}

void erase_progress_bar() {
	if (!isProgressBarActive) return;

	isProgressBarActive = false;
}

void assert(const bool& assertion, const std::string& message, const std::string& filePath, const std::string& function, const std::string& source, const uint32_t& line) {
	if (!assertion) throw Error{
		.message = message,
		.filePath = filePath,
		.function = function,
		.source = source,
		.line = std::to_string(line)
	};
}

std::string byte_to_string(const uint8_t& byte) {
	char string[] = "0x00";
	uint8_t digit;
	
	for (uint8_t i = 2; i--;) {
		digit = (byte >> i * 4) & 0xF;
		string[3 - i] = digit >= 0xA ? 'A' + digit - 0xA : '0' + digit;
	}

	return string;
}

std::string get_file_extension(const std::string& filename) {
	size_t dotPos = filename.find_last_of('.');
	if (dotPos == std::string::npos) return "";
	return filename.substr(dotPos);
}

std::string get_filename_without_extension(const std::string& filename) {
	size_t dotPos = filename.find_last_of('.');
	if (dotPos == std::string::npos) return filename;
	return filename.substr(0, dotPos);
}

bool create_directory(const std::string& path) {
	struct stat st;
	if (stat(path.c_str(), &st) == 0) {
		return S_ISDIR(st.st_mode);
	}
	return mkdir(path.c_str(), 0755) == 0;
}

bool file_exists(const std::string& path) {
	struct stat st;
	return stat(path.c_str(), &st) == 0;
}

bool is_directory(const std::string& path) {
	struct stat st;
	if (stat(path.c_str(), &st) != 0) return false;
	return S_ISDIR(st.st_mode);
}
