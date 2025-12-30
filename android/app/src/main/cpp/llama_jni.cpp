/**
 * llama_jni.cpp - JNI Bridge for llama.cpp
 * Guild of Smiths - Offline AI Module
 * 
 * Provides JNI interface for Kotlin/Java to interact with llama.cpp
 * for on-device LLM inference.
 */

#include <jni.h>
#include <string>
#include <android/log.h>
#include <mutex>
#include <atomic>

#define LOG_TAG "LlamaJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)

#ifndef LLAMA_STUB
// Real llama.cpp implementation
#include "llama.h"
#include "common.h"

static llama_model* g_model = nullptr;
static llama_context* g_ctx = nullptr;
static std::mutex g_mutex;
static std::atomic<bool> g_model_loaded(false);
static std::atomic<bool> g_cancel_generation(false);

#else
// Stub implementation when llama.cpp is not available
static bool g_model_loaded = false;
static std::mutex g_mutex;
static bool g_cancel_generation = false;
#endif

extern "C" {

/**
 * Initialize llama backend (call once at app start)
 */
JNIEXPORT jboolean JNICALL
Java_com_guildofsmiths_trademesh_ai_LlamaInference_nativeInit(
    JNIEnv* env,
    jobject /* this */
) {
    LOGI("Initializing llama backend");
    
#ifndef LLAMA_STUB
    llama_backend_init();
    LOGI("llama backend initialized successfully");
    return JNI_TRUE;
#else
    LOGW("Using stub implementation - llama.cpp not compiled");
    return JNI_TRUE;
#endif
}

/**
 * Load a GGUF model from the given path
 * 
 * @param modelPath Path to the .gguf model file
 * @param nCtx Context size (token window)
 * @param nThreads Number of threads to use
 * @return true if model loaded successfully
 */
JNIEXPORT jboolean JNICALL
Java_com_guildofsmiths_trademesh_ai_LlamaInference_nativeLoadModel(
    JNIEnv* env,
    jobject /* this */,
    jstring modelPath,
    jint nCtx,
    jint nThreads
) {
    std::lock_guard<std::mutex> lock(g_mutex);
    
    const char* path = env->GetStringUTFChars(modelPath, nullptr);
    LOGI("Loading model from: %s", path);
    
#ifndef LLAMA_STUB
    // Unload existing model if any
    if (g_ctx != nullptr) {
        llama_free(g_ctx);
        g_ctx = nullptr;
    }
    if (g_model != nullptr) {
        llama_free_model(g_model);
        g_model = nullptr;
    }
    
    // Model parameters
    llama_model_params model_params = llama_model_default_params();
    model_params.n_gpu_layers = 0; // CPU only for mobile
    
    // Load model
    g_model = llama_load_model_from_file(path, model_params);
    if (g_model == nullptr) {
        LOGE("Failed to load model from: %s", path);
        env->ReleaseStringUTFChars(modelPath, path);
        return JNI_FALSE;
    }
    
    // Context parameters
    llama_context_params ctx_params = llama_context_default_params();
    ctx_params.n_ctx = nCtx > 0 ? nCtx : 2048;
    ctx_params.n_threads = nThreads > 0 ? nThreads : 4;
    ctx_params.n_threads_batch = nThreads > 0 ? nThreads : 4;
    
    // Create context
    g_ctx = llama_new_context_with_model(g_model, ctx_params);
    if (g_ctx == nullptr) {
        LOGE("Failed to create context");
        llama_free_model(g_model);
        g_model = nullptr;
        env->ReleaseStringUTFChars(modelPath, path);
        return JNI_FALSE;
    }
    
    g_model_loaded = true;
    LOGI("Model loaded successfully. Context size: %d, Threads: %d", nCtx, nThreads);
#else
    g_model_loaded = true;
    LOGW("Stub: Model would be loaded from %s", path);
#endif
    
    env->ReleaseStringUTFChars(modelPath, path);
    return JNI_TRUE;
}

/**
 * Generate text from a prompt
 * 
 * @param prompt Input prompt string
 * @param maxTokens Maximum tokens to generate
 * @param temperature Sampling temperature (0.0 - 1.0)
 * @return Generated text
 */
JNIEXPORT jstring JNICALL
Java_com_guildofsmiths_trademesh_ai_LlamaInference_nativeGenerate(
    JNIEnv* env,
    jobject /* this */,
    jstring prompt,
    jint maxTokens,
    jfloat temperature
) {
    std::lock_guard<std::mutex> lock(g_mutex);
    
    if (!g_model_loaded) {
        LOGE("Model not loaded");
        return env->NewStringUTF("[Error: Model not loaded]");
    }
    
    const char* prompt_cstr = env->GetStringUTFChars(prompt, nullptr);
    std::string result;
    
    g_cancel_generation = false;
    
#ifndef LLAMA_STUB
    if (g_ctx == nullptr || g_model == nullptr) {
        env->ReleaseStringUTFChars(prompt, prompt_cstr);
        return env->NewStringUTF("[Error: Context not initialized]");
    }
    
    LOGI("Generating response for prompt: %.50s...", prompt_cstr);
    
    // Tokenize the prompt
    std::vector<llama_token> tokens(strlen(prompt_cstr) + 1);
    int n_tokens = llama_tokenize(g_model, prompt_cstr, strlen(prompt_cstr), 
                                   tokens.data(), tokens.size(), true, false);
    if (n_tokens < 0) {
        LOGE("Tokenization failed");
        env->ReleaseStringUTFChars(prompt, prompt_cstr);
        return env->NewStringUTF("[Error: Tokenization failed]");
    }
    tokens.resize(n_tokens);
    
    // Clear KV cache
    llama_kv_cache_clear(g_ctx);
    
    // Decode prompt
    llama_batch batch = llama_batch_init(512, 0, 1);
    for (int i = 0; i < n_tokens; i++) {
        llama_batch_add(batch, tokens[i], i, { 0 }, false);
    }
    batch.logits[batch.n_tokens - 1] = true;
    
    if (llama_decode(g_ctx, batch) != 0) {
        LOGE("Prompt decoding failed");
        llama_batch_free(batch);
        env->ReleaseStringUTFChars(prompt, prompt_cstr);
        return env->NewStringUTF("[Error: Decoding failed]");
    }
    
    // Generate tokens
    int n_cur = batch.n_tokens;
    int n_gen = 0;
    
    while (n_gen < maxTokens && !g_cancel_generation) {
        // Sample next token
        llama_token new_token = llama_sample_token_greedy(g_ctx, 
            llama_get_logits_ith(g_ctx, batch.n_tokens - 1));
        
        // Check for end of generation
        if (llama_token_is_eog(g_model, new_token)) {
            break;
        }
        
        // Convert token to string
        char buf[128];
        int n = llama_token_to_piece(g_model, new_token, buf, sizeof(buf), false);
        if (n > 0) {
            result.append(buf, n);
        }
        
        // Prepare next batch
        llama_batch_clear(batch);
        llama_batch_add(batch, new_token, n_cur, { 0 }, true);
        
        if (llama_decode(g_ctx, batch) != 0) {
            LOGE("Token decoding failed");
            break;
        }
        
        n_cur++;
        n_gen++;
    }
    
    llama_batch_free(batch);
    LOGI("Generated %d tokens", n_gen);
#else
    // Stub response for testing
    result = "[Stub Response] Model not compiled. Your prompt was: ";
    result += std::string(prompt_cstr).substr(0, 50);
    result += "...";
    LOGW("Stub: Would generate response for prompt");
#endif
    
    env->ReleaseStringUTFChars(prompt, prompt_cstr);
    return env->NewStringUTF(result.c_str());
}

/**
 * Cancel ongoing generation
 */
JNIEXPORT void JNICALL
Java_com_guildofsmiths_trademesh_ai_LlamaInference_nativeCancelGeneration(
    JNIEnv* env,
    jobject /* this */
) {
    LOGI("Cancelling generation");
#ifndef LLAMA_STUB
    g_cancel_generation = true;
#endif
}

/**
 * Unload the model and free resources
 */
JNIEXPORT void JNICALL
Java_com_guildofsmiths_trademesh_ai_LlamaInference_nativeUnloadModel(
    JNIEnv* env,
    jobject /* this */
) {
    std::lock_guard<std::mutex> lock(g_mutex);
    
    LOGI("Unloading model");
    
#ifndef LLAMA_STUB
    if (g_ctx != nullptr) {
        llama_free(g_ctx);
        g_ctx = nullptr;
    }
    if (g_model != nullptr) {
        llama_free_model(g_model);
        g_model = nullptr;
    }
#endif
    
    g_model_loaded = false;
    LOGI("Model unloaded");
}

/**
 * Check if model is currently loaded
 */
JNIEXPORT jboolean JNICALL
Java_com_guildofsmiths_trademesh_ai_LlamaInference_nativeIsModelLoaded(
    JNIEnv* env,
    jobject /* this */
) {
    return g_model_loaded ? JNI_TRUE : JNI_FALSE;
}

/**
 * Free llama backend resources (call at app shutdown)
 */
JNIEXPORT void JNICALL
Java_com_guildofsmiths_trademesh_ai_LlamaInference_nativeFree(
    JNIEnv* env,
    jobject /* this */
) {
    LOGI("Freeing llama backend");
    
#ifndef LLAMA_STUB
    // Unload model first
    Java_com_guildofsmiths_trademesh_ai_LlamaInference_nativeUnloadModel(env, nullptr);
    llama_backend_free();
#endif
    
    LOGI("llama backend freed");
}

/**
 * Get model info (vocab size, context size, etc.)
 */
JNIEXPORT jstring JNICALL
Java_com_guildofsmiths_trademesh_ai_LlamaInference_nativeGetModelInfo(
    JNIEnv* env,
    jobject /* this */
) {
#ifndef LLAMA_STUB
    if (g_model == nullptr) {
        return env->NewStringUTF("{}");
    }
    
    int n_vocab = llama_n_vocab(g_model);
    int n_ctx = g_ctx != nullptr ? llama_n_ctx(g_ctx) : 0;
    
    char info[256];
    snprintf(info, sizeof(info), 
             "{\"vocab_size\":%d,\"context_size\":%d,\"loaded\":true}",
             n_vocab, n_ctx);
    return env->NewStringUTF(info);
#else
    return env->NewStringUTF("{\"stub\":true,\"loaded\":false}");
#endif
}

} // extern "C"
