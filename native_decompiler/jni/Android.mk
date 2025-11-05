LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_MODULE := luajit-decompiler
LOCAL_SRC_FILES := main.cpp \
                   bytecode/bytecode.cpp \
                   bytecode/prototype.cpp \
                   bytecode/unprot.cpp \
                   ast/ast.cpp \
                   lua/lua.cpp

LOCAL_C_INCLUDES := $(LOCAL_PATH) \
                    $(LOCAL_PATH)/bytecode \
                    $(LOCAL_PATH)/ast \
                    $(LOCAL_PATH)/lua

LOCAL_CPPFLAGS := -std=c++20 -fexceptions -frtti -Wno-switch
LOCAL_LDLIBS := -llog -landroid

include $(BUILD_SHARED_LIBRARY)