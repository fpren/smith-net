package com.guildofsmiths.trademesh.data

import java.security.MessageDigest
import java.security.Signature
import java.security.KeyPair
import java.util.Base64
import kotlin.math.max

/**
 * Message classes that require cord-based guarantees.
 */
enum class CordMessageClass {
    STATUS,      // System status updates
    WORK_LOG,    // Labor/time entries
    COMMAND,     // Executable commands
    DECISION,    // Approvals, rejections
    ALERT,       // Notifications, warnings
    AI_SUMMARY,  // AI-generated summaries
    CHAT         // Regular conversation (may not need cord guarantees)
}

/**
 * CordEntry: Append-only, immutable entry in the cord-based state log.
 * Provides provable correctness for legal and financial records.
 *
 * Cord entries are:
 * - Content-addressed (MessageID derived from content)
 * - Deterministically ordered (Lamport timestamp + author counter)
 * - Cryptographically signed
 * - Immutable once committed
 */
data class CordEntry(
    // Content-addressed ID (collision-proof hash of content)
    val messageId: String,

    // Cord structure
    val cordId: String,           // Global cord identity
    val hubId: String,            // Permission domain (beacon/project)
    val channelId: String,        // Organizational lane
    val threadId: String? = null, // Optional thread reference

    // Author information
    val authorId: String,
    val authorName: String,

    // Message classification
    val messageClass: CordMessageClass,

    // Payload (serialized content)
    val payload: String,          // JSON-serialized payload
    val payloadType: String,      // "message", "time_entry", "job_update", etc.

    // Deterministic ordering (Lamport clock)
    val lamportTimestamp: Long,   // Lamport timestamp
    val authorCounter: Int,       // Author's local counter for tie-breaking

    // Wall-clock metadata (not used for ordering)
    val wallClockTime: Long,      // System.currentTimeMillis()

    // Cryptographic integrity
    val signature: String,        // Base64-encoded signature of above fields

    // Delivery telemetry (not part of integrity)
    val deliveryMarker: String? = null, // "· online", "· sub", "· sub→online"

    // Commit metadata
    val committedAt: Long = System.currentTimeMillis()
) {

    companion object {
        /**
         * Generate content-addressed MessageID from entry content.
         * Hash includes: authorId, messageClass, payload, lamportTimestamp, authorCounter
         */
        /**
         * Generate transport-independent MessageID from semantic content only.
         * This ensures the same semantic message has the same ID regardless of transport path.
         * Hash includes: authorId, messageClass, payload
         */
        fun generateSemanticMessageId(
            authorId: String,
            messageClass: CordMessageClass,
            payload: String
        ): String {
            val semanticContent = "$authorId|$messageClass|$payload"
            val digest = MessageDigest.getInstance("SHA-256")
            val hash = digest.digest(semanticContent.toByteArray(Charsets.UTF_8))
            return Base64.getUrlEncoder().encodeToString(hash).substring(0, 22) // URL-safe, 22 chars
        }

        /**
         * Generate transport-dependent MessageID (for ordering within semantic duplicates).
         * Includes timing data for when the same semantic message arrives via different paths.
         * Hash includes: authorId, messageClass, payload, lamportTimestamp, authorCounter
         */
        fun generateMessageId(
            authorId: String,
            messageClass: CordMessageClass,
            payload: String,
            lamportTimestamp: Long,
            authorCounter: Int
        ): String {
            val content = "$authorId|$messageClass|$payload|$lamportTimestamp|$authorCounter"
            val digest = MessageDigest.getInstance("SHA-256")
            val hash = digest.digest(content.toByteArray(Charsets.UTF_8))
            return Base64.getUrlEncoder().encodeToString(hash).substring(0, 22) // URL-safe, 22 chars
        }

        /**
         * Create a signed CordEntry with pre-computed semantic MessageID.
         * Used for transport-independent deduplication.
         */
        fun createSignedWithSemanticId(
            semanticMessageId: String,
            cordId: String,
            hubId: String,
            channelId: String,
            authorId: String,
            authorName: String,
            messageClass: CordMessageClass,
            payload: String,
            payloadType: String,
            lamportTimestamp: Long,
            authorCounter: Int,
            threadId: String? = null,
            deliveryMarker: String? = null,
            keyPair: KeyPair
        ): CordEntry {
            // Create signature data using the semantic MessageID
            val signatureData = "$semanticMessageId|$cordId|$hubId|$channelId|$authorId|$authorName|$messageClass|$payload|$payloadType|$lamportTimestamp|$authorCounter"
            val signature = Signature.getInstance("SHA256withRSA")
            signature.initSign(keyPair.private)
            signature.update(signatureData.toByteArray(Charsets.UTF_8))
            val signatureBytes = signature.sign()

            return CordEntry(
                messageId = semanticMessageId,
                cordId = cordId,
                hubId = hubId,
                channelId = channelId,
                threadId = threadId,
                authorId = authorId,
                authorName = authorName,
                messageClass = messageClass,
                payload = payload,
                payloadType = payloadType,
                lamportTimestamp = lamportTimestamp,
                authorCounter = authorCounter,
                wallClockTime = System.currentTimeMillis(),
                signature = Base64.getEncoder().encodeToString(signatureBytes),
                deliveryMarker = deliveryMarker
            )
        }

        /**
         * Create a signed CordEntry from data (legacy method).
         */
        fun createSigned(
            cordId: String,
            hubId: String,
            channelId: String,
            authorId: String,
            authorName: String,
            messageClass: CordMessageClass,
            payload: String,
            payloadType: String,
            lamportTimestamp: Long,
            authorCounter: Int,
            threadId: String? = null,
            deliveryMarker: String? = null,
            keyPair: KeyPair
        ): CordEntry {
            val messageId = generateMessageId(authorId, messageClass, payload, lamportTimestamp, authorCounter)

            // Create signature data (everything except the signature field itself)
            val signatureData = "$messageId|$cordId|$hubId|$channelId|$authorId|$authorName|$messageClass|$payload|$payloadType|$lamportTimestamp|$authorCounter"
            val signature = Signature.getInstance("SHA256withRSA")
            signature.initSign(keyPair.private)
            signature.update(signatureData.toByteArray(Charsets.UTF_8))
            val signatureBytes = signature.sign()

            return CordEntry(
                messageId = messageId,
                cordId = cordId,
                hubId = hubId,
                channelId = channelId,
                threadId = threadId,
                authorId = authorId,
                authorName = authorName,
                messageClass = messageClass,
                payload = payload,
                payloadType = payloadType,
                lamportTimestamp = lamportTimestamp,
                authorCounter = authorCounter,
                wallClockTime = System.currentTimeMillis(),
                signature = Base64.getEncoder().encodeToString(signatureBytes),
                deliveryMarker = deliveryMarker
            )
        }

        /**
         * Verify the cryptographic signature of a cord entry.
         */
        fun verifySignature(entry: CordEntry, publicKey: java.security.PublicKey): Boolean {
            return try {
                val signatureData = "${entry.messageId}|${entry.cordId}|${entry.hubId}|${entry.channelId}|${entry.authorId}|${entry.authorName}|${entry.messageClass}|${entry.payload}|${entry.payloadType}|${entry.lamportTimestamp}|${entry.authorCounter}"
                val signature = Signature.getInstance("SHA256withRSA")
                signature.initVerify(publicKey)
                signature.update(signatureData.toByteArray(Charsets.UTF_8))
                val signatureBytes = Base64.getDecoder().decode(entry.signature)
                signature.verify(signatureBytes)
            } catch (e: Exception) {
                false
            }
        }
    }

    /**
     * Verify this entry's signature (requires author's public key).
     */
    fun verifySignature(publicKey: java.security.PublicKey): Boolean =
        verifySignature(this, publicKey)

    /**
     * Get the canonical ordering key for this entry.
     */
    fun orderingKey(): String =
        "${lamportTimestamp.toString().padStart(20, '0')}|$authorId|$authorCounter"

    /**
     * Check if this entry happened-before another entry (Lamport ordering).
     */
    fun happenedBefore(other: CordEntry): Boolean =
        lamportTimestamp < other.lamportTimestamp ||
        (lamportTimestamp == other.lamportTimestamp && authorCounter < other.authorCounter)

    /**
     * Merge Lamport timestamps (return the maximum).
     */
    fun mergeLamportTimestamp(otherTimestamp: Long): Long =
        max(lamportTimestamp, otherTimestamp)
}

/**
 * Lamport clock implementation for deterministic ordering.
 */
class LamportClock(private var timestamp: Long = 0) {

    /**
     * Get the current timestamp and increment for next event.
     */
    fun tick(): Long {
        timestamp++
        return timestamp
    }

    /**
     * Update timestamp based on received message (merge rule).
     */
    fun update(receivedTimestamp: Long) {
        timestamp = max(timestamp, receivedTimestamp) + 1
    }

    /**
     * Get current timestamp without incrementing.
     */
    fun current(): Long = timestamp

    /**
     * Reset clock (for testing only).
     */
    fun reset(newTimestamp: Long = 0) {
        timestamp = newTimestamp
    }
}
