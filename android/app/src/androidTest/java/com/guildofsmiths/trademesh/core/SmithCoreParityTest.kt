package com.guildofsmiths.trademesh.core

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.guildofsmiths.trademesh.data.VectorClock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.security.MessageDigest

/**
 * On-device parity gate (Android half of M1). Proves the wasm ROM run through
 * WAMR computes vector-clock merge/compare and SHA-256 identically to the
 * legacy Kotlin implementation + java.security. Mirrors the backend Jest gate
 * in backend/src/__tests__/smithcore-parity.test.ts so both hosts are pinned
 * to the same ROM behaviour.
 *
 * Skips (rather than fails) when the lib was built without WAMR (stub) so the
 * suite stays green until cpp/wamr is vendored.
 */
@RunWith(AndroidJUnit4::class)
class SmithCoreParityTest {

    @Before
    fun setup() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        SmithCore.initFromAssets(ctx)
        assumeTrue("smithcore ROM not ready (WAMR not vendored) -- skipping", SmithCore.isReady())
    }

    private fun norm(c: Map<String, Int>): Map<String, Int> = c.filterValues { it != 0 }

    private val cases: List<Pair<Map<String, Int>, Map<String, Int>>> = listOf(
        emptyMap<String, Int>() to emptyMap(),
        mapOf("a" to 1) to emptyMap(),
        emptyMap<String, Int>() to mapOf("a" to 1),
        mapOf("a" to 1) to mapOf("a" to 2),
        mapOf("a" to 2, "b" to 1) to mapOf("a" to 1, "b" to 2),   // concurrent
        mapOf("a" to 3, "b" to 3) to mapOf("a" to 1, "b" to 1),   // dominates
        mapOf("a" to 1, "ab" to 1) to mapOf("ab" to 2),           // prefix ids
        mapOf("café" to 3) to mapOf("café" to 1, "x" to 5),       // multibyte id
    )

    @Test
    fun merge_matches_legacy() {
        for ((a, b) in cases) {
            val core = SmithCore.vclockMerge(a, b)
            assertNotNull("merge returned null for $a,$b", core)
            val legacy = VectorClock(a).mergeLocal(VectorClock(b)).state
            assertEquals(norm(legacy), norm(core!!))
        }
    }

    @Test
    fun compare_matches_legacy() {
        for ((a, b) in cases) {
            val legacy = VectorClock(a).compareLocalTo(VectorClock(b))
            assertEquals("compare mismatch for $a,$b", legacy, SmithCore.vclockCompare(a, b))
        }
    }

    @Test
    fun sha256_matches_java() {
        for (len in intArrayOf(0, 1, 55, 56, 64, 65, 1000)) {
            val data = ByteArray(len) { (it * 31 + 7).toByte() }
            val core = SmithCore.sha256(data)
            assertNotNull(core)
            val expected = MessageDigest.getInstance("SHA-256").digest(data)
            assertEquals(expected.toList(), core!!.toList())
        }
    }
}
