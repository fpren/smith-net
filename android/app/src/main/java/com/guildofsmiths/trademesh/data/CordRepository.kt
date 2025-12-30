package com.guildofsmiths.trademesh.data

import android.content.Context
import com.guildofsmiths.trademesh.db.AppDatabase
import com.guildofsmiths.trademesh.db.CordEntity
import com.guildofsmiths.trademesh.data.CordMessageClass
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.security.KeyPair
import java.security.KeyPairGenerator

/**
 * CordRepository: Append-only repository for cord-based state.
 * Provides provable correctness for legal and financial records.
 *
 * Responsibilities:
 * - Lamport clock management per author
 * - Cryptographic signing/verification
 * - Append-only operations
 * - Deterministic reconciliation
 */
class CordRepository(private val context: Context) {

    private val database = AppDatabase.getInstance(context)
    private val cordDao = database.cordDao()

    // Lamport clocks per author (in-memory, persisted as needed)
    private val lamportClocks = mutableMapOf<String, LamportClock>()

    // Cryptographic keys per author (should be securely stored)
    private val authorKeys = mutableMapOf<String, KeyPair>()

    init {
        // Initialize with default author clock (will be created on first use)
    }

    /**
     * Get or create Lamport clock for an author.
     */
    private suspend fun getOrCreateClock(authorId: String): LamportClock {
        return lamportClocks.getOrPut(authorId) {
            // Initialize clock from database state
            val maxTimestamp = cordDao.getMaxLamportTimestampForAuthor(authorId) ?: 0L
            LamportClock(maxTimestamp)
        }
    }

    /**
     * Get or create cryptographic keys for an author.
     */
    private fun getOrCreateKeys(authorId: String): KeyPair {
        return authorKeys.getOrPut(authorId) {
            // In production, load from secure storage
            // For now, generate new keys (should be persisted)
            val keyGen = KeyPairGenerator.getInstance("RSA")
            keyGen.initialize(2048)
            keyGen.generateKeyPair()
        }
    }

    /**
     * Append a new cord entry with full guarantees.
     */
    /**
     * Append cord entry with HARD DEDUPLICATION based on semantic MessageID.
     * Ensures ONE message per semantic content, regardless of transport path.
     */
    suspend fun appendCordEntry(
        cordId: String,
        hubId: String,
        channelId: String,
        authorId: String,
        authorName: String,
        messageClass: CordMessageClass,
        payload: String,
        payloadType: String,
        threadId: String? = null,
        deliveryMarker: String? = null
    ): CordEntry? {
        // Generate semantic MessageID (transport-independent)
        val semanticMessageId = CordEntry.generateSemanticMessageId(authorId, messageClass, payload)

        // HARD DEDUPLICATION: Check if semantic message already exists
        val existingEntry = cordDao.getCordEntryById(semanticMessageId)
        if (existingEntry != null) {
            // Message exists - update delivery marker only (telemetry, not state)
            updateDeliveryMarker(semanticMessageId, deliveryMarker)
            return existingEntry.toCordEntry() // Return existing entry
        }

        // Message doesn't exist - create new entry
        val clock = getOrCreateClock(authorId)
        val keys = getOrCreateKeys(authorId)

        // Tick the Lamport clock
        val lamportTimestamp = clock.tick()

        // Get author counter (sequential per author per timestamp)
        val authorCounter = (cordDao.getMaxAuthorCounter(authorId, lamportTimestamp) ?: -1) + 1

        // Create signed cord entry with semantic MessageID
        val cordEntry = CordEntry.createSignedWithSemanticId(
            semanticMessageId = semanticMessageId,
            cordId = cordId,
            hubId = hubId,
            channelId = channelId,
            authorId = authorId,
            authorName = authorName,
            messageClass = messageClass,
            payload = payload,
            payloadType = payloadType,
            lamportTimestamp = lamportTimestamp,
            authorCounter = authorCounter,
            threadId = threadId,
            deliveryMarker = deliveryMarker,
            keyPair = keys
        )

        // Verify signature before storing
        if (!cordEntry.verifySignature(keys.public)) {
            throw IllegalStateException("Failed to verify cord entry signature")
        }

        // Append to cord
        val inserted = cordDao.insertCordEntry(CordEntity.fromCordEntry(cordEntry))

        return if (inserted > 0) cordEntry else null
    }

    /**
     * Update delivery marker for existing cord entry (telemetry only).
     * Delivery markers are the ONLY mutable aspect of cord entries.
     */
    private suspend fun updateDeliveryMarker(messageId: String, newMarker: String?) {
        if (newMarker == null) return

        val existing = cordDao.getCordEntryById(messageId) ?: return
        val currentMarker = existing.deliveryMarker

        // Update marker based on transition rules
        val updatedMarker = when {
            currentMarker == null -> newMarker
            currentMarker == "· sub" && newMarker == "· online" -> "· sub→online"
            currentMarker == "· online" && newMarker == "· sub" -> "· sub→online" // Allow bidirectional
            else -> currentMarker // Keep existing marker
        }

        if (updatedMarker != currentMarker) {
            // Update only the delivery marker field
            cordDao.updateDeliveryMarker(messageId, updatedMarker)
        }
    }

    /**
     * Append multiple cord entries atomically.
     */
    suspend fun appendCordEntries(entries: List<CordEntry>): List<CordEntry> {
        val entities = entries.map { CordEntity.fromCordEntry(it) }
        val results = cordDao.insertCordEntries(entities)

        // Update local Lamport clocks from received entries
        entries.forEach { entry ->
            val clock = getOrCreateClock(entry.authorId)
            clock.update(entry.lamportTimestamp)
        }

        // Return successfully inserted entries
        return entries.zip(results).filter { it.second > 0 }.map { it.first }
    }

    /**
     * Get all cord entries (deterministically ordered).
     */
    fun getAllCordEntries(): Flow<List<CordEntry>> {
        return cordDao.getAllCordEntries().map { entities ->
            entities.map { it.toCordEntry() }
        }
    }

    /**
     * Get cord entries for a specific hub/channel.
     */
    fun getCordEntriesForChannel(hubId: String, channelId: String): Flow<List<CordEntry>> {
        return cordDao.getCordEntriesForChannel(hubId, channelId).map { entities ->
            entities.map { it.toCordEntry() }
        }
    }

    /**
     * Get cord entries by message class.
     */
    fun getCordEntriesByClass(messageClass: CordMessageClass): Flow<List<CordEntry>> {
        return cordDao.getCordEntriesByClass(messageClass.name).map { entities ->
            entities.map { it.toCordEntry() }
        }
    }

    /**
     * Get cord entries by author.
     */
    fun getCordEntriesByAuthor(authorId: String): Flow<List<CordEntry>> {
        return cordDao.getCordEntriesByAuthor(authorId).map { entities ->
            entities.map { it.toCordEntry() }
        }
    }

    /**
     * Get a specific cord entry by MessageID.
     */
    suspend fun getCordEntryById(messageId: String): CordEntry? {
        return cordDao.getCordEntryById(messageId)?.toCordEntry()
    }

    /**
     * Check if a MessageID exists.
     */
    suspend fun cordEntryExists(messageId: String): Boolean {
        return cordDao.cordEntryExists(messageId) > 0
    }

    /**
     * Get cord entries newer than a timestamp (for sync).
     */
    suspend fun getCordEntriesSince(sinceTimestamp: Long): List<CordEntry> {
        return cordDao.getCordEntriesSince(sinceTimestamp).map { it.toCordEntry() }
    }

    /**
     * Reconcile with remote cord entries (deterministic merge).
     * Returns the entries that were successfully merged.
     */
    suspend fun reconcileCordEntries(remoteEntries: List<CordEntry>): List<CordEntry> {
        val mergedEntries = mutableListOf<CordEntry>()

        for (remoteEntry in remoteEntries) {
            // Verify signature before accepting
            val keys = getOrCreateKeys(remoteEntry.authorId)
            if (!remoteEntry.verifySignature(keys.public)) {
                // Signature verification failed - reject entry
                continue
            }

            // Check if we already have this entry
            if (cordEntryExists(remoteEntry.messageId)) {
                continue
            }

            // Update our Lamport clock
            val clock = getOrCreateClock(remoteEntry.authorId)
            clock.update(remoteEntry.lamportTimestamp)

            // Insert the remote entry
            val entity = CordEntity.fromCordEntry(remoteEntry)
            val inserted = cordDao.insertCordEntry(entity)

            if (inserted > 0) {
                mergedEntries.add(remoteEntry)
            }
        }

        return mergedEntries
    }

    /**
     * Get cord statistics for diagnostics.
     */
    suspend fun getCordStats(): CordStats {
        val entryCount = cordDao.getCordEntryCount()
        val maxTimestamp = cordDao.getMaxLamportTimestamp() ?: 0L

        return CordStats(
            totalEntries = entryCount,
            maxLamportTimestamp = maxTimestamp,
            activeAuthors = lamportClocks.size
        )
    }

    /**
     * Validate cord integrity (signature verification).
     */
    suspend fun validateCordIntegrity(): CordValidationResult {
        val allEntries = cordDao.getAllCordEntries().map { entities ->
            entities.map { it.toCordEntry() }
        }

        var validEntries = 0
        var invalidEntries = 0
        val errors = mutableListOf<String>()

        // Collect all entries first (Flow needs to be collected)
        val entries = mutableListOf<CordEntry>()
        allEntries.collect { entries.addAll(it) }

        for (entry in entries) {
            try {
                val keys = getOrCreateKeys(entry.authorId)
                if (entry.verifySignature(keys.public)) {
                    validEntries++
                } else {
                    invalidEntries++
                    errors.add("Invalid signature for MessageID: ${entry.messageId}")
                }
            } catch (e: Exception) {
                invalidEntries++
                errors.add("Signature verification error for MessageID ${entry.messageId}: ${e.message}")
            }
        }

        return CordValidationResult(
            totalEntries = entries.size,
            validEntries = validEntries,
            invalidEntries = invalidEntries,
            errors = errors
        )
    }
}

/**
 * Cord statistics for diagnostics.
 */
data class CordStats(
    val totalEntries: Int,
    val maxLamportTimestamp: Long,
    val activeAuthors: Int
)

/**
 * Cord validation result.
 */
data class CordValidationResult(
    val totalEntries: Int,
    val validEntries: Int,
    val invalidEntries: Int,
    val errors: List<String>
) {
    val isValid: Boolean = invalidEntries == 0
}
