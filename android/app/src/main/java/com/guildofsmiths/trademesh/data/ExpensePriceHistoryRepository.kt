package com.guildofsmiths.trademesh.data

import com.guildofsmiths.trademesh.ui.jobboard.Job
import com.guildofsmiths.trademesh.ui.jobboard.JobExpense

/**
 * Answers "what price did I pay last time for this item?" across all jobs.
 * Rebuilt in-memory on each lookup by scanning the caller-provided job list —
 * no separate persistence; JobExpense records are the source of truth.
 */
object ExpensePriceHistoryRepository {

    data class PriceHit(
        val unitCost: Double,
        val vendor: String,
        val unit: String,
        val incurredAt: Long,
        val description: String
    )

    /**
     * Return up to [limit] most-recent prior prices for items matching
     * [description] (normalized). If [categoryId] is non-null, only matches
     * within that category.
     */
    fun lookup(
        allJobs: List<Job>,
        description: String,
        categoryId: String? = null,
        limit: Int = 5
    ): List<PriceHit> {
        val key = normalize(description)
        if (key.isBlank()) return emptyList()

        return allJobs.asSequence()
            .flatMap { it.expenses.asSequence() }
            .filter { it.unitCost > 0.0 && !it.aiEstimated }
            .filter { categoryId == null || it.category == categoryId }
            .filter { normalize(it.description) == key }
            .sortedByDescending { it.incurredAt }
            .take(limit)
            .map {
                PriceHit(
                    unitCost = it.unitCost,
                    vendor = it.vendor,
                    unit = it.unit,
                    incurredAt = it.incurredAt,
                    description = it.description
                )
            }
            .toList()
    }

    /** Most recent price, or null if none. */
    fun mostRecent(
        allJobs: List<Job>,
        description: String,
        categoryId: String? = null
    ): PriceHit? = lookup(allJobs, description, categoryId, limit = 1).firstOrNull()

    private fun normalize(s: String): String =
        s.lowercase()
            .replace(Regex("[^a-z0-9 ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
}
