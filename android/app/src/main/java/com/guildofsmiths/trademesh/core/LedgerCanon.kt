package com.guildofsmiths.trademesh.core

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** The 12 hashed fields of a SummaryArtifact (id/createdAt are not hashed). */
data class LedgerArtifactInput(
    val serial: String,
    val intentVersionId: String,
    val scopeStatement: String,
    val workPerformed: List<String>,
    val laborRecorded: List<String>,
    val materialsUsed: List<String>,
    val contextualNotes: List<String>,
    val totalCost: Double,
    val totalHours: Double,
    val jobIds: List<String>,
    val timeEntryIds: List<String>,
    val chatMessageIds: List<String>,
)

/**
 * Kotlin mirror of backend/src/ledgerCanonical.ts (v2). Pure byte encoding --
 * the ROM only does the SHA-256 over these bytes -- so this runs without WAMR.
 * Proven byte-identical to the TS encoder by core/testdata/ledger-golden.json.
 *
 * Wire format (all integers little-endian):
 *   [0x53 0x4D 0x43 0x01 0x02]       -- "SMC" + ABI(1) + FORMAT_V2(2)
 *   serial                            -- u32 len + UTF-8 bytes
 *   intentVersionId                   -- u32 len + UTF-8 bytes
 *   scopeStatement                    -- u32 len + UTF-8 bytes
 *   workPerformed                     -- u32 count + each string
 *   laborRecorded                     -- u32 count + each string
 *   materialsUsed                     -- u32 count + each string
 *   contextualNotes                   -- u32 count + each string
 *   totalCost  as i64 cent-units      -- round(totalCost * 100), 8 bytes LE
 *   totalHours as i64 centi-hours     -- round(totalHours * 100), 8 bytes LE
 *   jobIds (sorted by UTF-8 bytes)    -- u32 count + each string
 *   timeEntryIds (sorted)             -- u32 count + each string
 *   chatMessageIds (sorted)           -- u32 count + each string
 */
object LedgerCanon {
    private const val ABI: Byte = 0x01
    private const val FORMAT_V2: Byte = 0x02

    fun encode(a: LedgerArtifactInput): ByteArray {
        val out = ByteArrayOutputStream()
        // Header: "SMC" + abi + format -- mirrors Buffer.from([0x53,0x4d,0x43,ABI,FORMAT_V2])
        out.write(byteArrayOf(0x53, 0x4D, 0x43, ABI, FORMAT_V2))
        writeStr(out, a.serial)
        writeStr(out, a.intentVersionId)
        writeStr(out, a.scopeStatement)
        writeStrArray(out, a.workPerformed)
        writeStrArray(out, a.laborRecorded)
        writeStrArray(out, a.materialsUsed)
        writeStrArray(out, a.contextualNotes)
        // Math.round(Double): Long -- same tie-breaking as JS Math.round for non-negative values.
        writeI64(out, Math.round(a.totalCost * 100))
        writeI64(out, Math.round(a.totalHours * 100))
        writeStrArray(out, sortedByUtf8(a.jobIds))
        writeStrArray(out, sortedByUtf8(a.timeEntryIds))
        writeStrArray(out, sortedByUtf8(a.chatMessageIds))
        return out.toByteArray()
    }

    // ── private helpers ────────────────────────────────────────────────────

    private fun u32(v: Int): ByteArray =
        ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(v).array()

    /** u32 LE length prefix + UTF-8 bytes -- mirrors TS encStr. */
    private fun writeStr(out: ByteArrayOutputStream, s: String) {
        val b = s.toByteArray(Charsets.UTF_8)
        out.write(u32(b.size))
        out.write(b)
    }

    /** u32 LE count + each string -- mirrors TS encStrArray. */
    private fun writeStrArray(out: ByteArrayOutputStream, arr: List<String>) {
        out.write(u32(arr.size))
        for (s in arr) writeStr(out, s)
    }

    /** i64 LE -- mirrors TS writeBigInt64LE. */
    private fun writeI64(out: ByteArrayOutputStream, v: Long) {
        out.write(ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(v).array())
    }

    /**
     * Unsigned byte-by-byte compare matching Node's Buffer.compare:
     * shorter array is less on a common-prefix tie.
     */
    private fun cmpBytes(a: ByteArray, b: ByteArray): Int {
        val n = minOf(a.size, b.size)
        for (i in 0 until n) {
            val ai = a[i].toInt() and 0xFF
            val bi = b[i].toInt() and 0xFF
            if (ai != bi) return ai - bi
        }
        return a.size - b.size
    }

    /** Sort strings by their UTF-8 byte representation (unsigned) -- mirrors TS sortedByUtf8. */
    private fun sortedByUtf8(arr: List<String>): List<String> =
        arr.sortedWith { x, y -> cmpBytes(x.toByteArray(Charsets.UTF_8), y.toByteArray(Charsets.UTF_8)) }
}
