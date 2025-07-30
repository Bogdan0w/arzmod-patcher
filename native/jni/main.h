#pragma once

#include <cstdint>
#include <jni.h>

extern char libName[256];
extern uintptr_t libHandle;

extern char raknetName[256];
extern uintptr_t raknetHandle;

extern JavaVM* g_jvm;
extern jobject g_activity;
extern char g_package[256];

#define HOOK_LIBRARY "libsamp.so"
#define HOOK_RAKNET "libraknet.so"
