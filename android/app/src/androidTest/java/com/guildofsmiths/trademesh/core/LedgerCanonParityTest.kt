package com.guildofsmiths.trademesh.core

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Cross-host proof (Android half of M2): the Kotlin v2 encoder produces bytes
 * identical to the backend TS encoder for the committed golden vectors. The
 * encoder is pure Kotlin, so this runs without WAMR -- it does not gate on the
 * ROM being vendored.
 */
@RunWith(AndroidJUnit4::class)
class LedgerCanonParityTest {

    private fun loadGolden(): JSONObject {
        // androidTest assets are served from the *test* context, not the app.
        val ctx = InstrumentationRegistry.getInstrumentation().context
        val bytes = ctx.assets.open("ledger-golden.json").use { it.readBytes() }
        return JSONObject(String(bytes, Charsets.UTF_8))
    }

    private fun toHex(b: ByteArray): String =
        b.joinToString("") { "%02x".format(it.toInt() and 0xFF) }

    private fun strList(o: JSONObject, name: String): List<String> {
        val a = o.getJSONArray(name)
        return (0 until a.length()).map { a.getString(it) }
    }

    private fun inputFrom(o: JSONObject) = LedgerArtifactInput(
        serial = o.getString("serial"),
        intentVersionId = o.getString("intentVersionId"),
        scopeStatement = o.getString("scopeStatement"),
        workPerformed = strList(o, "workPerformed"),
        laborRecorded = strList(o, "laborRecorded"),
        materialsUsed = strList(o, "materialsUsed"),
        contextualNotes = strList(o, "contextualNotes"),
        totalCost = o.getDouble("totalCost"),
        totalHours = o.getDouble("totalHours"),
        jobIds = strList(o, "jobIds"),
        timeEntryIds = strList(o, "timeEntryIds"),
        chatMessageIds = strList(o, "chatMessageIds"),
    )

    @Test
    fun kotlinEncoderMatchesGoldenBytes() {
        val vectors = loadGolden().getJSONArray("vectors")
        for (i in 0 until vectors.length()) {
            val v = vectors.getJSONObject(i)
            val input = inputFrom(v.getJSONObject("artifact"))
            assertEquals(
                "vector ${v.getString("label")} canonical bytes",
                v.getString("canonicalHex"),
                toHex(LedgerCanon.encode(input)),
            )
        }
    }
}
