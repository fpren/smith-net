package com.guildofsmiths.trademesh.ai

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.io.File
import java.io.RandomAccessFile
import java.security.MessageDigest

object SmithAIAuditLog {

    private const val TAG = "SmithAIAuditLog"
    private const val FILE_NAME = "smithai_audit.jsonl"

    data class Entry(
        val timestamp: Long,
        val toolName: String,
        val arguments: String,
        val approved: Boolean,
        val resultSummary: String,
        val prevHash: String,
        val hash: String
    )

    @Synchronized
    fun append(
        context: Context,
        toolName: String,
        arguments: String,
        approved: Boolean,
        resultSummary: String
    ): Entry {
        val file = File(context.filesDir, FILE_NAME)
        val prevHash = readLastHash(file)
        val timestamp = System.currentTimeMillis()
        val payload = JSONObject().apply {
            put("ts", timestamp)
            put("tool", toolName)
            put("args", arguments)
            put("approved", approved)
            put("result", resultSummary)
            put("prev", prevHash)
        }
        val hash = sha256(payload.toString())
        payload.put("hash", hash)
        try {
            file.appendText(payload.toString() + "\n")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to append audit row", e)
        }
        return Entry(timestamp, toolName, arguments, approved, resultSummary, prevHash, hash)
    }

    private fun readLastHash(file: File): String {
        if (!file.exists() || file.length() == 0L) return GENESIS_HASH
        return try {
            RandomAccessFile(file, "r").use { raf ->
                val length = raf.length()
                var pos = length - 1
                if (pos < 0) return GENESIS_HASH
                while (pos > 0) {
                    raf.seek(pos)
                    if (raf.readByte().toInt() == '\n'.code && pos != length - 1) {
                        pos++
                        break
                    }
                    pos--
                }
                raf.seek(pos)
                val line = raf.readLine() ?: return GENESIS_HASH
                JSONObject(line).optString("hash", GENESIS_HASH)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read last hash, restarting chain", e)
            GENESIS_HASH
        }
    }

    private fun sha256(input: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val bytes = md.digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private const val GENESIS_HASH = "0000000000000000000000000000000000000000000000000000000000000000"
}
