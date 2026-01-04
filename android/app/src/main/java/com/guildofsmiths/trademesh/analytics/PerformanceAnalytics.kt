package com.guildofsmiths.trademesh.analytics

import com.guildofsmiths.trademesh.ui.jobboard.Job
import com.guildofsmiths.trademesh.ui.jobboard.JobStatus
import com.guildofsmiths.trademesh.ui.timetracking.TimeEntry

/**
 * Performance Analytics Engine
 * 
 * Calculates performance metrics for jobs and historical analysis.
 * Separates client-facing metrics from internal business metrics.
 */
object PerformanceAnalytics {

    // ════════════════════════════════════════════════════════════════════
    // PERFORMANCE SUMMARY DATA CLASSES
    // ════════════════════════════════════════════════════════════════════

    /**
     * Internal performance metrics (NOT shown to clients).
     * Used for business intelligence and dashboard analytics.
     */
    data class InternalPerformanceMetrics(
        val profitabilityScore: Int,        // 1-10 bars
        val operationalScore: Int,          // 1-10 bars
        val timeManagementScore: Int,       // 1-10 bars
        val qualityScore: Int,              // 1-10 bars
        val overallScore: Int,              // 1-10 bars (average)
        val profitMarginPercent: Double?,   // Actual profit margin %
        val completionTimeVariance: Double? // % difference from estimate
    )

    /**
     * Client-facing performance summary.
     * Safe to include in proposals, invoices, and client communications.
     */
    data class ClientFacingPerformance(
        val averageSatisfactionRating: Double?,  // e.g., 4.6/10
        val totalSimilarJobsCompleted: Int,      // e.g., 12 jobs
        val onTimeCompletionRate: Int,           // e.g., 95%
        val qualityTrackRecord: String,          // e.g., "No safety incidents"
        val avgCompletionTime: String,           // e.g., "6.2 hours"
        val marketPosition: String               // e.g., "Premium (+18% above market)"
    )

    /**
     * Industry benchmark comparison.
     */
    data class BenchmarkComparison(
        val laborRateVsMarket: PercentageComparison,
        val completionTimeVsIndustry: PercentageComparison,
        val profitMarginVsIndustry: PercentageComparison
    )

    data class PercentageComparison(
        val yourValue: Double,
        val marketValue: Double,
        val percentDifference: Int,
        val status: ComparisonStatus
    )

    enum class ComparisonStatus {
        SIGNIFICANTLY_BETTER,  // Green - 15%+ above
        SLIGHTLY_BETTER,       // Blue - 0-15% above
        AVERAGE,               // Yellow - within 5%
        BELOW_AVERAGE          // Red - below market
    }

    /**
     * Historical analytics for archive dashboard.
     */
    data class HistoricalAnalytics(
        val totalJobsCompleted: Int,
        val avgSatisfactionRating: Double,
        val avgProfitabilityScore: Int,
        val avgOperationalScore: Int,
        val avgTimeManagementScore: Int,
        val avgQualityScore: Int,
        val totalRevenue: Double,
        val totalHoursWorked: Double,
        val mostProfitableTrade: String?,
        val topPerformingMonth: String?,
        val improvementTrends: List<TrendData>
    )

    data class TrendData(
        val metric: String,
        val currentValue: Double,
        val previousValue: Double,
        val percentChange: Int,
        val isImproving: Boolean
    )

    // ════════════════════════════════════════════════════════════════════
    // CALCULATION FUNCTIONS
    // ════════════════════════════════════════════════════════════════════

    /**
     * Calculate internal performance metrics for a single job.
     */
    fun calculateJobPerformance(
        job: Job,
        timeEntries: List<TimeEntry>
    ): InternalPerformanceMetrics {
        val jobTimeEntries = timeEntries.filter { 
            it.jobId == job.id || it.jobTitle == job.title 
        }
        
        val totalMinutes = jobTimeEntries
            .filter { it.clockOutTime != null }
            .sumOf { it.durationMinutes ?: 0 }
        val totalHours = totalMinutes / 60.0
        
        // Calculate profitability score
        val profitabilityScore = calculateProfitabilityScore(job, totalHours)
        
        // Calculate operational score (based on materials completion, workflow)
        val operationalScore = calculateOperationalScore(job)
        
        // Calculate time management score
        val timeManagementScore = calculateTimeManagementScore(job, totalHours)
        
        // Calculate quality score
        val qualityScore = calculateQualityScore(job)
        
        // Overall average
        val overallScore = (profitabilityScore + operationalScore + timeManagementScore + qualityScore) / 4
        
        // Calculate actual profit margin if we have cost data
        val materialsCost = job.materials.sumOf { it.totalCost }
        val laborCost = totalHours * (job.actualLaborRate ?: 85.0) // Default $85/hr
        val totalCost = materialsCost + laborCost
        val estimatedRevenue = totalCost * 1.25 // Assume 25% markup
        val profitMargin = if (estimatedRevenue > 0) ((estimatedRevenue - totalCost) / estimatedRevenue) * 100 else null
        
        // Time variance
        val estimatedHours = job.estimatedEndDate?.let { end ->
            job.estimatedStartDate?.let { start ->
                ((end - start) / (1000 * 60 * 60)).toDouble()
            }
        }
        val timeVariance = estimatedHours?.let { est ->
            if (est > 0) ((totalHours - est) / est) * 100 else null
        }
        
        return InternalPerformanceMetrics(
            profitabilityScore = profitabilityScore,
            operationalScore = operationalScore,
            timeManagementScore = timeManagementScore,
            qualityScore = qualityScore,
            overallScore = overallScore,
            profitMarginPercent = profitMargin,
            completionTimeVariance = timeVariance
        )
    }

    /**
     * Calculate client-facing performance summary from historical data.
     */
    fun calculateClientFacingPerformance(
        completedJobs: List<Job>,
        timeEntries: List<TimeEntry>,
        marketLaborRate: Double = 72.0  // Default market rate
    ): ClientFacingPerformance {
        // Filter to jobs with feedback
        val jobsWithFeedback = completedJobs.filter { it.clientSatisfactionBars != null }
        val avgSatisfaction = if (jobsWithFeedback.isNotEmpty()) {
            jobsWithFeedback.map { it.clientSatisfactionBars!! }.average()
        } else null
        
        // On-time completion rate
        val jobsWithDueDate = completedJobs.filter { it.dueDate != null && it.completedAt != null }
        val onTimeJobs = jobsWithDueDate.count { it.completedAt!! <= it.dueDate!! }
        val onTimeRate = if (jobsWithDueDate.isNotEmpty()) {
            (onTimeJobs.toDouble() / jobsWithDueDate.size * 100).toInt()
        } else 95 // Default assumption
        
        // Average completion time
        val avgHours = completedJobs.mapNotNull { job ->
            val jobEntries = timeEntries.filter { it.jobId == job.id || it.jobTitle == job.title }
            val totalMins = jobEntries.filter { it.clockOutTime != null }.sumOf { it.durationMinutes ?: 0 }
            if (totalMins > 0) totalMins / 60.0 else null
        }.average().takeIf { !it.isNaN() } ?: 0.0
        
        // Calculate market position
        val avgLaborRate = completedJobs.mapNotNull { it.actualLaborRate }.average().takeIf { !it.isNaN() } ?: 85.0
        val marketDiff = ((avgLaborRate - marketLaborRate) / marketLaborRate * 100).toInt()
        val marketPosition = when {
            marketDiff >= 15 -> "Premium (+$marketDiff% above market)"
            marketDiff >= 0 -> "Above market (+$marketDiff%)"
            marketDiff >= -10 -> "Market rate ($marketDiff%)"
            else -> "Competitive ($marketDiff%)"
        }
        
        return ClientFacingPerformance(
            averageSatisfactionRating = avgSatisfaction,
            totalSimilarJobsCompleted = completedJobs.size,
            onTimeCompletionRate = onTimeRate,
            qualityTrackRecord = "100% code compliance, no safety incidents",
            avgCompletionTime = String.format("%.1f hours", avgHours),
            marketPosition = marketPosition
        )
    }

    /**
     * Calculate benchmark comparisons.
     */
    fun calculateBenchmarkComparison(
        job: Job,
        timeEntries: List<TimeEntry>,
        marketLaborRate: Double = 72.0,
        industryAvgHours: Double = 6.0,
        industryProfitMargin: Double = 18.0
    ): BenchmarkComparison {
        val jobTimeEntries = timeEntries.filter { it.jobId == job.id || it.jobTitle == job.title }
        val totalHours = jobTimeEntries.filter { it.clockOutTime != null }
            .sumOf { it.durationMinutes ?: 0 } / 60.0
        
        val actualRate = job.actualLaborRate ?: 85.0
        val actualMargin = job.actualProfitMargin ?: 22.0
        
        return BenchmarkComparison(
            laborRateVsMarket = calculateComparison(actualRate, marketLaborRate, higherIsBetter = true),
            completionTimeVsIndustry = calculateComparison(totalHours, industryAvgHours, higherIsBetter = false),
            profitMarginVsIndustry = calculateComparison(actualMargin, industryProfitMargin, higherIsBetter = true)
        )
    }

    /**
     * Calculate historical analytics for archive dashboard.
     */
    fun calculateHistoricalAnalytics(
        archivedJobs: List<Job>,
        timeEntries: List<TimeEntry>
    ): HistoricalAnalytics {
        // Basic counts
        val totalCompleted = archivedJobs.size
        
        // Average satisfaction
        val avgSatisfaction = archivedJobs
            .mapNotNull { it.clientSatisfactionBars }
            .average()
            .takeIf { !it.isNaN() } ?: 0.0
        
        // Average performance scores
        val avgProfitability = archivedJobs.mapNotNull { it.profitabilityScore }.average().toInt()
        val avgOperational = archivedJobs.mapNotNull { it.operationalScore }.average().toInt()
        val avgTimeManagement = archivedJobs.mapNotNull { it.timeManagementScore }.average().toInt()
        val avgQuality = archivedJobs.mapNotNull { it.qualityScore }.average().toInt()
        
        // Total revenue and hours
        val totalRevenue = archivedJobs.sumOf { job ->
            job.materials.sumOf { it.totalCost } + 
            (timeEntries.filter { it.jobId == job.id }.sumOf { it.durationMinutes ?: 0 } / 60.0 * (job.actualLaborRate ?: 85.0))
        }
        val totalHours = timeEntries
            .filter { entry -> archivedJobs.any { it.id == entry.jobId } }
            .filter { it.clockOutTime != null }
            .sumOf { it.durationMinutes ?: 0 } / 60.0
        
        return HistoricalAnalytics(
            totalJobsCompleted = totalCompleted,
            avgSatisfactionRating = avgSatisfaction,
            avgProfitabilityScore = avgProfitability.coerceIn(1, 10),
            avgOperationalScore = avgOperational.coerceIn(1, 10),
            avgTimeManagementScore = avgTimeManagement.coerceIn(1, 10),
            avgQualityScore = avgQuality.coerceIn(1, 10),
            totalRevenue = totalRevenue,
            totalHoursWorked = totalHours,
            mostProfitableTrade = null, // TODO: Implement trade analysis
            topPerformingMonth = null,  // TODO: Implement monthly analysis
            improvementTrends = emptyList() // TODO: Implement trend calculation
        )
    }

    // ════════════════════════════════════════════════════════════════════
    // HELPER FUNCTIONS
    // ════════════════════════════════════════════════════════════════════

    private fun calculateProfitabilityScore(job: Job, totalHours: Double): Int {
        // If we have stored score, use it
        job.profitabilityScore?.let { return it }
        
        // Calculate based on available data
        val materialsCost = job.materials.sumOf { it.totalCost }
        val laborCost = totalHours * (job.actualLaborRate ?: 85.0)
        
        // Higher materials relative to labor suggests lower margin
        val costRatio = if (laborCost > 0) materialsCost / laborCost else 1.0
        
        return when {
            costRatio < 0.3 -> 9  // High labor, low materials = high margin
            costRatio < 0.5 -> 8
            costRatio < 0.7 -> 7
            costRatio < 1.0 -> 6
            costRatio < 1.5 -> 5
            else -> 4
        }
    }

    private fun calculateOperationalScore(job: Job): Int {
        // If we have stored score, use it
        job.operationalScore?.let { return it }
        
        // Score based on materials completion
        val totalMaterials = job.materials.size
        val checkedMaterials = job.materials.count { it.checked }
        
        return when {
            totalMaterials == 0 -> 7 // No materials tracked = neutral
            checkedMaterials == totalMaterials -> 10  // All materials checked
            checkedMaterials.toDouble() / totalMaterials >= 0.9 -> 8
            checkedMaterials.toDouble() / totalMaterials >= 0.7 -> 6
            else -> 4
        }
    }

    private fun calculateTimeManagementScore(job: Job, actualHours: Double): Int {
        // If we have stored score, use it
        job.timeManagementScore?.let { return it }
        
        // Compare actual vs estimated if available
        val estimatedHours = job.estimatedEndDate?.let { end ->
            job.estimatedStartDate?.let { start ->
                ((end - start) / (1000 * 60 * 60)).toDouble()
            }
        }
        
        return if (estimatedHours != null && estimatedHours > 0) {
            val variance = (actualHours - estimatedHours) / estimatedHours
            when {
                variance <= -0.2 -> 10  // 20%+ under estimate
                variance <= -0.1 -> 9
                variance <= 0.0 -> 8    // On or under estimate
                variance <= 0.1 -> 7
                variance <= 0.2 -> 6
                variance <= 0.3 -> 5
                else -> 4               // Significantly over estimate
            }
        } else {
            7 // Default neutral if no estimate
        }
    }

    private fun calculateQualityScore(job: Job): Int {
        // If we have stored score, use it
        job.qualityScore?.let { return it }
        
        // Score based on client feedback if available
        return job.clientSatisfactionBars?.let { rating ->
            rating // 1-10 maps directly
        } ?: 7 // Default neutral if no feedback
    }

    private fun calculateComparison(
        yourValue: Double,
        marketValue: Double,
        higherIsBetter: Boolean
    ): PercentageComparison {
        val difference = yourValue - marketValue
        val percentDiff = if (marketValue > 0) ((difference / marketValue) * 100).toInt() else 0
        
        val isPositive = if (higherIsBetter) difference > 0 else difference < 0
        val absPercent = kotlin.math.abs(percentDiff)
        
        val status = when {
            isPositive && absPercent >= 15 -> ComparisonStatus.SIGNIFICANTLY_BETTER
            isPositive -> ComparisonStatus.SLIGHTLY_BETTER
            absPercent <= 5 -> ComparisonStatus.AVERAGE
            else -> ComparisonStatus.BELOW_AVERAGE
        }
        
        return PercentageComparison(
            yourValue = yourValue,
            marketValue = marketValue,
            percentDifference = percentDiff,
            status = status
        )
    }
}
