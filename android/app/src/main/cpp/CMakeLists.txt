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
