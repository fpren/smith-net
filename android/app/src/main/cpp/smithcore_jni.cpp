/*
 * smithcore_jni.cpp -- Android host binding for the SmithNet "ROM".
 *
 * Embeds WAMR (WebAssembly Micro Runtime, fast-interpreter) and runs the EXACT
 * same smithcore.wasm bytes the Node backend loads, so vector-clock merge /
 * compare are byte-identical across server and device (the determinism moat).
 * iOS will reuse the same runtime in interpreter mode (App-Store-legal: no JIT).
 *
 * Build modes (mirrors the llama_jni stub pattern in CMakeLists.txt):
 *   - SMITHCORE_WAMR defined  -> real runtime, links vendored cpp/wamr.
 *   - otherwise (stub)        -> every native call reports failure, so
 *                                SmithCore.kt stays not-ready and VectorClock.kt
 *                                degrades to the legacy Kotlin path.
 *
 * The wasm exposes its own arena (sc_reset/sc_alloc); we do not use WAMR's
 * module heap. Pointers exchanged with the wasm are app offsets translated to
 * native addresses via wasm_runtime_addr_app_to_native.
 */
#include <jni.h>
#include <android/log.h>
#include <cstring>
#include <vector>
#include <mutex>

#define LOG_TAG "SmithCoreJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

#define SC_CMP_ERR 2  /* matches core/include/smithcore.h */

extern "C" {

#if defined(SMITHCORE_WAMR)
#include "wasm_export.h"

namespace {
std::mutex g_mutex;
bool g_inited = false;
wasm_module_t g_module = nullptr;
wasm_module_inst_t g_inst = nullptr;
wasm_exec_env_t g_env = nullptr;
std::vector<uint8_t> g_wasm_bytes; // WAMR loads in place; keep bytes alive.

wasm_function_inst_t fn(const char *name) {
    return wasm_runtime_lookup_function(g_inst, name);
}

// Call an export with up to 6 i32 args; returns the i32 result (argv[0]).
// Returns false on a trap.
bool call_i32(const char *name, uint32_t *argv, uint32_t argc, int32_t *out) {
    wasm_function_inst_t f = fn(name);
    if (!f) { LOGE("export not found: %s", name); return false; }
    if (!wasm_runtime_call_wasm(g_env, f, argc, argv)) {
        LOGE("trap calling %s: %s", name, wasm_runtime_get_exception(g_inst));
        return false;
    }
    if (out) *out = (int32_t) argv[0];
    return true;
}

// sc_alloc(len) -> app offset (0 on OOM)
uint32_t sc_alloc(uint32_t len) {
    uint32_t argv[1] = { len };
    int32_t r = 0;
    if (!call_i32("sc_alloc", argv, 1, &r)) return 0;
    return (uint32_t) r;
}

void sc_reset() {
    wasm_function_inst_t f = fn("sc_reset");
    if (f) { uint32_t argv[1] = {0}; wasm_runtime_call_wasm(g_env, f, 0, argv); }
}

// Stage bytes into a fresh wasm allocation; returns app offset or 0.
uint32_t stage(const uint8_t *data, uint32_t len) {
    uint32_t off = sc_alloc(len ? len : 1);
    if (off == 0) return 0;
    if (len) {
        void *native = wasm_runtime_addr_app_to_native(g_inst, off);
        if (!native) return 0;
        memcpy(native, data, len);
    }
    return off;
}

} // namespace

JNIEXPORT jint JNICALL
Java_com_guildofsmiths_trademesh_core_SmithCore_nativeInitFromBytes(
        JNIEnv *env, jobject, jbyteArray wasm) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (g_inited) return 1;

    if (!wasm_runtime_init()) { LOGE("wasm_runtime_init failed"); return 0; }

    jsize n = env->GetArrayLength(wasm);
    g_wasm_bytes.resize((size_t) n);
    env->GetByteArrayRegion(wasm, 0, n, reinterpret_cast<jbyte *>(g_wasm_bytes.data()));

    char err[192];
    g_module = wasm_runtime_load(g_wasm_bytes.data(), (uint32_t) n, err, sizeof(err));
    if (!g_module) { LOGE("load failed: %s", err); return 0; }

    // 64KB stack, 0 heap: the ROM manages its own arena, so no module heap.
    g_inst = wasm_runtime_instantiate(g_module, 64 * 1024, 0, err, sizeof(err));
    if (!g_inst) { LOGE("instantiate failed: %s", err); return 0; }

    g_env = wasm_runtime_create_exec_env(g_inst, 64 * 1024);
    if (!g_env) { LOGE("create_exec_env failed"); return 0; }

    int32_t ver = 0;
    uint32_t argv[1] = {0};
    if (!call_i32("sc_version", argv, 0, &ver) || ver != 1) {
        LOGE("ABI mismatch: %d", ver);
        return 0;
    }
    g_inited = true;
    LOGI("smithcore ROM loaded via WAMR (ABI %d)", ver);
    return 1;
}

JNIEXPORT jint JNICALL
Java_com_guildofsmiths_trademesh_core_SmithCore_nativeVersion(JNIEnv *, jobject) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (!g_inited) return -1;
    uint32_t argv[1] = {0};
    int32_t v = -1;
    call_i32("sc_version", argv, 0, &v);
    return v;
}

// Returns the merged canonical clock as a byte[], or null on failure.
JNIEXPORT jbyteArray JNICALL
Java_com_guildofsmiths_trademesh_core_SmithCore_nativeVclockMerge(
        JNIEnv *env, jobject, jbyteArray a, jbyteArray b) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (!g_inited) return nullptr;

    jsize al = env->GetArrayLength(a), bl = env->GetArrayLength(b);
    std::vector<uint8_t> ab(al), bb(bl);
    env->GetByteArrayRegion(a, 0, al, reinterpret_cast<jbyte *>(ab.data()));
    env->GetByteArrayRegion(b, 0, bl, reinterpret_cast<jbyte *>(bb.data()));

    sc_reset();
    uint32_t ap = stage(ab.data(), (uint32_t) al);
    uint32_t bp = stage(bb.data(), (uint32_t) bl);
    uint32_t cap = (uint32_t) (al + bl + 8);
    uint32_t op = sc_alloc(cap);
    if ((al && !ap) || (bl && !bp) || !op) return nullptr;

    uint32_t argv[6] = { ap, (uint32_t) al, bp, (uint32_t) bl, op, cap };
    int32_t outLen = -1;
    if (!call_i32("sc_vclock_merge", argv, 6, &outLen) || outLen < 0) return nullptr;

    void *native = wasm_runtime_addr_app_to_native(g_inst, op);
    if (!native) return nullptr;
    jbyteArray result = env->NewByteArray(outLen);
    env->SetByteArrayRegion(result, 0, outLen, reinterpret_cast<const jbyte *>(native));
    return result;
}

// Returns -1/0/1, or SC_CMP_ERR (2) on failure.
JNIEXPORT jint JNICALL
Java_com_guildofsmiths_trademesh_core_SmithCore_nativeVclockCompare(
        JNIEnv *env, jobject, jbyteArray a, jbyteArray b) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (!g_inited) return SC_CMP_ERR;

    jsize al = env->GetArrayLength(a), bl = env->GetArrayLength(b);
    std::vector<uint8_t> ab(al), bb(bl);
    env->GetByteArrayRegion(a, 0, al, reinterpret_cast<jbyte *>(ab.data()));
    env->GetByteArrayRegion(b, 0, bl, reinterpret_cast<jbyte *>(bb.data()));

    sc_reset();
    uint32_t ap = stage(ab.data(), (uint32_t) al);
    uint32_t bp = stage(bb.data(), (uint32_t) bl);
    if ((al && !ap) || (bl && !bp)) return SC_CMP_ERR;

    uint32_t argv[4] = { ap, (uint32_t) al, bp, (uint32_t) bl };
    int32_t r = SC_CMP_ERR;
    if (!call_i32("sc_vclock_compare", argv, 4, &r)) return SC_CMP_ERR;
    return r;
}

// Returns 32 bytes, or null on failure.
JNIEXPORT jbyteArray JNICALL
Java_com_guildofsmiths_trademesh_core_SmithCore_nativeSha256(
        JNIEnv *env, jobject, jbyteArray data) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (!g_inited) return nullptr;

    jsize dl = env->GetArrayLength(data);
    std::vector<uint8_t> d(dl);
    env->GetByteArrayRegion(data, 0, dl, reinterpret_cast<jbyte *>(d.data()));

    sc_reset();
    uint32_t dp = stage(d.data(), (uint32_t) dl);
    uint32_t op = sc_alloc(32);
    if ((dl && !dp) || !op) return nullptr;

    uint32_t argv[3] = { dp, (uint32_t) dl, op };
    int32_t rc = -1;
    if (!call_i32("sc_sha256", argv, 3, &rc) || rc != 0) return nullptr;

    void *native = wasm_runtime_addr_app_to_native(g_inst, op);
    if (!native) return nullptr;
    jbyteArray result = env->NewByteArray(32);
    env->SetByteArrayRegion(result, 0, 32, reinterpret_cast<const jbyte *>(native));
    return result;
}

#else  /* ---------- stub: WAMR not vendored; report not-ready ---------- */

JNIEXPORT jint JNICALL
Java_com_guildofsmiths_trademesh_core_SmithCore_nativeInitFromBytes(JNIEnv *, jobject, jbyteArray) {
    LOGI("smithcore_jni built without WAMR (stub) -- using legacy vclock");
    return 0;
}
JNIEXPORT jint JNICALL
Java_com_guildofsmiths_trademesh_core_SmithCore_nativeVersion(JNIEnv *, jobject) { return -1; }
JNIEXPORT jbyteArray JNICALL
Java_com_guildofsmiths_trademesh_core_SmithCore_nativeVclockMerge(JNIEnv *, jobject, jbyteArray, jbyteArray) { return nullptr; }
JNIEXPORT jint JNICALL
Java_com_guildofsmiths_trademesh_core_SmithCore_nativeVclockCompare(JNIEnv *, jobject, jbyteArray, jbyteArray) { return SC_CMP_ERR; }
JNIEXPORT jbyteArray JNICALL
Java_com_guildofsmiths_trademesh_core_SmithCore_nativeSha256(JNIEnv *, jobject, jbyteArray) { return nullptr; }

#endif

} // extern "C"
