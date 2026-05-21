package com.guildofsmiths.trademesh.core

import android.content.Context
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * SmithCore - Android host binding for the SmithNet "ROM" (smithcore.wasm).
 *
 * Loads the EXACT same wasm bytes the Node backend loads (bundled at
 * assets/smithcore.wasm) and runs them through WAMR via smithcore_jni. Vector
 * clock merge/compare therefore use one implementation across server and
 * device -- the determinism moat. See backend/src/core/smithCore.ts for the
 * mirror binding and core/include/smithcore.h for the ABI + wire format.
 *
 * Readiness-gated: if the native lib was built without WAMR (stub) or the wasm
 * fails to load, isReady() stays false and VectorClock falls back to its legacy
 * Kotlin implementation (which the parity gate proves identical).
 */
object SmithCore {

    private const val TAG = "SmithCore"
    private const val SC_CMP_ERR = 2  // matches core/include/smithcore.h

    private var libLoaded = false
    private var ready = false

    init {
        try {
            System.loadLibrary("smithcore_jni")
            libLoaded = true
            Log.i(TAG, "smithcore_jni loaded")
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "failed to load smithcore_jni", e)
        }
    }

    // ── native (JNI) ──────────────────────────────────────────────────────
    private external fun nativeInitFromBytes(wasm: ByteArray): Int
    private external fun nativeVersion(): Int
    private external fun nativeVclockMerge(a: ByteArray, b: ByteArray): ByteArray?
    private external fun nativeVclockCompare(a: ByteArray, b: ByteArray): Int
    private external fun nativeSha256(data: ByteArray): ByteArray?

    // ── lifecycle ─────────────────────────────────────────────────────────
    /** Load + instantiate the ROM from assets. Idempotent; safe to call at app start. */
    @Synchronized
    fun initFromAssets(context: Context): Boolean {
        if (ready) return true
        if (!libLoaded) return false
        return try {
            val bytes = context.assets.open("smithcore.wasm").use { it.readBytes() }
            ready = nativeInitFromBytes(bytes) == 1
            if (ready) Log.i(TAG, "smithcore ROM ready (ABI ${nativeVersion()})")
            else Log.w(TAG, "smithcore ROM not ready (stub build or load failure)")
            ready
        } catch (e: Throwable) {
            Log.e(TAG, "smithcore init failed", e)
            false
        }
    }

    fun isReady(): Boolean = ready

    // ── public surface ────────────────────────────────────────────────────
    /** Merge two clocks via the ROM. Returns null if the core is unavailable. */
    fun vclockMerge(a: Map<String, Int>, b: Map<String, Int>): Map<String, Int>? {
        if (!ready) return null
        val out = nativeVclockMerge(encodeClock(a), encodeClock(b)) ?: return null
        return decodeClock(out)
    }

    /** Causal compare via the ROM. Returns -1/0/1, or SC_CMP_ERR (2) if unavailable. */
    fun vclockCompare(a: Map<String, Int>, b: Map<String, Int>): Int {
        if (!ready) return SC_CMP_ERR
        return nativeVclockCompare(encodeClock(a), encodeClock(b))
    }

    /** SHA-256 via the ROM. Returns null if the core is unavailable. */
    fun sha256(data: ByteArray): ByteArray? {
        if (!ready) return null
        return nativeSha256(data)
    }

    // ── canonical vector-clock codec (mirrors core/src/vclock.c & smithCore.ts) ──
    // Entries sorted ascending by id UTF-8 bytes (shorter is less on a prefix
    // tie), zero counts omitted. u16 n, then [u16 id_len][id][u32 count], all LE.
    private fun cmpBytes(a: ByteArray, b: ByteArray): Int {
        val n = minOf(a.size, b.size)
        for (i in 0 until n) {
            val ai = a[i].toInt() and 0xFF
            val bi = b[i].toInt() and 0xFF
            if (ai != bi) return ai - bi
        }
        return a.size - b.size
    }

    private class WireEntry(val id: ByteArray, val count: Int)

    private fun encodeClock(state: Map<String, Int>): ByteArray {
        val entries = state.entries
            .filter { it.value != 0 }
            .map { WireEntry(it.key.toByteArray(Charsets.UTF_8), it.value) }
            .sortedWith { x, y -> cmpBytes(x.id, y.id) }
        var size = 2
        for (e in entries) size += 2 + e.id.size + 4
        val buf = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN)
        buf.putShort(entries.size.toShort())
        for (e in entries) {
            buf.putShort(e.id.size.toShort())
            buf.put(e.id)
            buf.putInt(e.count)
        }
        return buf.array()
    }

    private fun decodeClock(bytes: ByteArray): Map<String, Int> {
        val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val n = bb.short.toInt() and 0xFFFF
        val out = LinkedHashMap<String, Int>(n)
        for (i in 0 until n) {
            val idLen = bb.short.toInt() and 0xFFFF
            val idBytes = ByteArray(idLen)
            bb.get(idBytes)
            val count = bb.int
            out[String(idBytes, Charsets.UTF_8)] = count
        }
        return out
    }
}
