package com.guildofsmiths.trademesh.data

import android.content.Context
import android.content.SharedPreferences
import com.guildofsmiths.trademesh.ui.jobboard.Job
import com.guildofsmiths.trademesh.ui.jobboard.JobStage
import org.json.JSONObject

data class ClientInfo(
    val name: String,
    val phone: String,
    val address: String,
    val jobCount: Int,
    val activeJobCount: Int,
    val latestStage: JobStage?,
    val totalEarned: Double
)

data class ClientOverride(
    val name: String,
    val phone: String,
    val address: String
)

object ClientRepository {

    private const val PREFS_NAME = "trademesh_clients"
    private const val PREFS_KEY = "client_overrides"
    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun getClients(allJobs: List<Job>): List<ClientInfo> {
        val jobClients = allJobs
            .filter { !it.clientName.isNullOrBlank() }
            .groupBy { it.clientName!!.trim() }
            .map { (name, jobs) -> buildClientInfo(name, jobs) }

        // Merge manual clients that have no jobs
        val jobClientNames = jobClients.map { it.name.lowercase() }.toSet()
        val manualOnly = getManualClients().filter { it.name.lowercase() !in jobClientNames }

        return (jobClients + manualOnly).sortedBy { it.name.lowercase() }
    }

    private fun buildClientInfo(name: String, jobs: List<Job>): ClientInfo {
        val override = getClientOverride(name)
        val latestJob = jobs.maxByOrNull { it.updatedAt }
        val activeJobs = jobs.filter { it.stage != JobStage.CLOSED }

        val totalEarned = jobs
            .filter { it.stage == JobStage.CLOSED }
            .sumOf { it.materials.sumOf { m -> m.totalCost } + (it.hourlyRate * 8) }

        return ClientInfo(
            name = override?.name ?: name,
            phone = override?.phone ?: latestJob?.clientPhone ?: "",
            address = override?.address ?: latestJob?.clientAddress ?: "",
            jobCount = jobs.size,
            activeJobCount = activeJobs.size,
            latestStage = latestJob?.stage,
            totalEarned = totalEarned
        )
    }

    fun getJobsForClient(name: String, allJobs: List<Job>): List<Job> {
        return allJobs
            .filter { it.clientName?.trim().equals(name.trim(), ignoreCase = true) }
            .sortedByDescending { it.updatedAt }
    }

    fun saveClientOverride(originalName: String, name: String, phone: String, address: String) {
        val overrides = loadOverrides()
        val override = JSONObject().apply {
            put("name", name)
            put("phone", phone)
            put("address", address)
        }
        overrides.put(originalName, override)
        prefs?.edit()?.putString(PREFS_KEY, overrides.toString())?.apply()
    }

    fun addManualClient(name: String, phone: String, address: String) {
        saveClientOverride(name, name, phone, address)
    }

    fun getClientOverride(name: String): ClientOverride? {
        val overrides = loadOverrides()
        val json = overrides.optJSONObject(name) ?: return null
        return ClientOverride(
            name = json.optString("name", name),
            phone = json.optString("phone", ""),
            address = json.optString("address", "")
        )
    }

    private fun getManualClients(): List<ClientInfo> {
        val overrides = loadOverrides()
        val manualClients = mutableListOf<ClientInfo>()
        val keys = overrides.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val json = overrides.optJSONObject(key) ?: continue
            manualClients.add(
                ClientInfo(
                    name = json.optString("name", key),
                    phone = json.optString("phone", ""),
                    address = json.optString("address", ""),
                    jobCount = 0,
                    activeJobCount = 0,
                    latestStage = null,
                    totalEarned = 0.0
                )
            )
        }
        return manualClients
    }

    private fun loadOverrides(): JSONObject {
        val json = prefs?.getString(PREFS_KEY, null) ?: return JSONObject()
        return try { JSONObject(json) } catch (_: Exception) { JSONObject() }
    }
}
