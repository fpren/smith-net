# CMakeLists.txt for llama.cpp JNI integration
# Guild of Smiths - Offline AI Module

cmake_minimum_required(VERSION 3.22.1)
project("llama_jni" VERSION 1.0.0 LANGUAGES CXX)

# C++17 standard
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Optimization flags for mobile
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -O3 -ffast-math")
set(CMAKE_CXX_FLAGS_RELEASE "${CMAKE_CXX_FLAGS_RELEASE} -O3 -DNDEBUG")

# ARM NEON support
if(${ANDROID_ABI} STREQUAL "arm64-v8a")
    set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -march=armv8-a")
elseif(${ANDROID_ABI} STREQUAL "armeabi-v7a")
    set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -mfpu=neon -mfloat-abi=softfp")
endif()

# llama.cpp source directory (will be downloaded/vendored)
set(LLAMA_DIR "${CMAKE_CURRENT_SOURCE_DIR}/llama.cpp")

# Check if llama.cpp exists, if not create stub
if(NOT EXISTS "${LLAMA_DIR}/llama.h")
    message(WARNING "llama.cpp not found at ${LLAMA_DIR}. Using stub implementation.")
    set(USE_STUB TRUE)
else()
    set(USE_STUB FALSE)
    
    # llama.cpp source files
    set(LLAMA_SOURCES
        ${LLAMA_DIR}/llama.cpp
        ${LLAMA_DIR}/ggml.c
        ${LLAMA_DIR}/ggml-alloc.c
        ${LLAMA_DIR}/ggml-backend.c
        ${LLAMA_DIR}/ggml-quants.c
        ${LLAMA_DIR}/common/common.cpp
        ${LLAMA_DIR}/common/sampling.cpp
        ${LLAMA_DIR}/common/grammar-parser.cpp
    )
    
    # Include directories
    include_directories(
        ${LLAMA_DIR}
        ${LLAMA_DIR}/common
    )
endif()

# JNI bridge source
set(JNI_SOURCES
    ${CMAKE_CURRENT_SOURCE_DIR}/llama_jni.cpp
)

# Create shared library
add_library(llama_jni SHARED ${JNI_SOURCES})

# Link libraries
find_library(log-lib log)
find_library(android-lib android)

target_link_libraries(llama_jni
    ${log-lib}
    ${android-lib}
)

# If llama.cpp exists, create and link the llama library
if(NOT USE_STUB)
    add_library(llama STATIC ${LLAMA_SOURCES})
    target_compile_definitions(llama PRIVATE
        GGML_USE_CPU
        NDEBUG
    )
    target_link_libraries(llama_jni llama)
endif()

# Compile definitions
target_compile_definitions(llama_jni PRIVATE
    $<$<BOOL:${USE_STUB}>:LLAMA_STUB>
)

# ════════════════════════════════════════════════════════════════════════════
# SmithNet WASM core ("ROM") JNI bridge -- runs the shared smithcore.wasm.
# ════════════════════════════════════════════════════════════════════════════
# Mirrors the llama stub pattern: if WAMR is not vendored at cpp/wamr, build a
# stub so the project still links and SmithCore.kt falls back to legacy vclock.
# Vendor with: git clone --depth 1 https://github.com/bytecodealliance/wasm-micro-runtime cpp/wamr
set(WAMR_DIR "${CMAKE_CURRENT_SOURCE_DIR}/wamr")

add_library(smithcore_jni SHARED ${CMAKE_CURRENT_SOURCE_DIR}/smithcore_jni.cpp)
target_link_libraries(smithcore_jni ${log-lib})

if(EXISTS "${WAMR_DIR}/core/iwasm/include/wasm_export.h")
    message(STATUS "WAMR found at ${WAMR_DIR} -- building real smithcore_jni")
    enable_language(C)  # WAMR runtime is C

    set(WAMR_BUILD_PLATFORM "android")
    if(${ANDROID_ABI} STREQUAL "arm64-v8a")
        set(WAMR_BUILD_TARGET "AARCH64")
    elseif(${ANDROID_ABI} STREQUAL "armeabi-v7a")
        set(WAMR_BUILD_TARGET "ARMV7")
    endif()
    # Fast interpreter only: iOS will reuse this config (App Store forbids JIT),
    # and the ROM has zero imports so no WASI/libc layer is needed.
    set(WAMR_BUILD_INTERP 1)
    set(WAMR_BUILD_FAST_INTERP 1)
    set(WAMR_BUILD_AOT 0)
    set(WAMR_BUILD_JIT 0)
    set(WAMR_BUILD_LIBC_BUILTIN 0)
    set(WAMR_BUILD_LIBC_WASI 0)
    set(WAMR_BUILD_MULTI_MODULE 0)

    include(${WAMR_DIR}/build-scripts/runtime_lib.cmake)
    add_library(vmlib STATIC ${WAMR_RUNTIME_LIB_SOURCE})
    target_include_directories(smithcore_jni PRIVATE ${WAMR_DIR}/core/iwasm/include)
    target_compile_definitions(smithcore_jni PRIVATE SMITHCORE_WAMR)
    target_link_libraries(smithcore_jni vmlib)
else()
    message(WARNING "WAMR not found at ${WAMR_DIR} -- building smithcore_jni STUB (legacy vclock fallback)")
endif()
