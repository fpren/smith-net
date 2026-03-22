package com.guildofsmiths.trademesh.ui.dashboard

import androidx.lifecycle.ViewModel
import com.guildofsmiths.trademesh.data.UserPreferences
import com.guildofsmiths.trademesh.ui.jobboard.Job
import com.guildofsmiths.trademesh.ui.jobboard.JobStage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class DashboardAlert(
    val jobId: String,
    val message: String,
    val type: AlertType
)

enum class AlertType { APPROVAL, OVERDUE_TASK, UNPAID_INVOICE, CLIENT_RESPONSE }

class DashboardViewModel : ViewModel() {
    private val _jobs = MutableStateFlow<List<Job>>(emptyList())
    val jobs: StateFlow<List<Job>> = _jobs.asStateFlow()

    private val _alerts = MutableStateFlow<List<DashboardAlert>>(emptyList())
    val alerts: StateFlow<List<DashboardAlert>> = _alerts.asStateFlow()

    fun loadJobs(allJobs: List<Job>) {
        _jobs.value = allJobs.filter { it.stage != JobStage.CLOSED }
        _alerts.value = buildAlerts(allJobs)
    }

    private fun buildAlerts(allJobs: List<Job>): List<DashboardAlert> {
        val alerts = mutableListOf<DashboardAlert>()
        allJobs.forEach { job ->
            when {
                job.stage == JobStage.PROPOSAL ->
                    alerts.add(DashboardAlert(job.id, "${job.clientName ?: job.title} — proposal awaiting response", AlertType.APPROVAL))
                job.stage == JobStage.INVOICE ->
                    alerts.add(DashboardAlert(job.id, "${job.clientName ?: job.title} — invoice unpaid", AlertType.UNPAID_INVOICE))
                job.stage == JobStage.APPROVED && (System.currentTimeMillis() - job.updatedAt) < 86400000 ->
                    alerts.add(DashboardAlert(job.id, "${job.clientName ?: job.title} — client approved!", AlertType.CLIENT_RESPONSE))
            }
        }
        return alerts
    }

    fun getActiveJobCount(): Int = _jobs.value.count { it.stage != JobStage.CLOSED }

    fun getOutstandingTotal(): Double = _jobs.value
        .filter { it.stage == JobStage.INVOICE }
        .sumOf { it.materials.sumOf { m -> m.totalCost } + (it.hourlyRate * 8) } // rough estimate

    fun getBusinessName(): String {
        val biz = UserPreferences.getBusinessName()
        return biz.ifBlank { UserPreferences.getDisplayName() }
    }
}
