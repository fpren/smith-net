package com.guildofsmiths.trademesh.data

import android.content.Context
import android.content.SharedPreferences
import com.guildofsmiths.trademesh.ui.jobboard.Job
import com.guildofsmiths.trademesh.ui.jobboard.JobRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Repository for managing collaborative shared jobs
 */
object SharedJobRepository {

    private const val PREFS_NAME = "shared_jobs"
    private const val KEY_SHARED_JOBS = "jobs"

    private var prefs: SharedPreferences? = null
    private var jobRepository: JobRepository? = null

    private val _sharedJobs = MutableStateFlow<List<SharedJob>>(emptyList())
    val sharedJobs: StateFlow<List<SharedJob>> = _sharedJobs.asStateFlow()

    /**
     * Initialize repository with context
     */
    fun init(context: Context, jobRepo: JobRepository) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        jobRepository = jobRepo
        loadSharedJobs()
    }

    /**
     * Create a shared job from an existing job
     */
    fun createSharedJob(
        jobId: String,
        collaborators: List<String>,
        leadCollaborator: String,
        collaborationMode: CollaborationMode = CollaborationMode.TEAM_EFFORT
    ): SharedJob? {
        val baseJob = jobRepository?.getJob(jobId) ?: return null

        // Get collaborator details from PeerRepository
        val collaboratorDetails = collaborators.mapNotNull { userId ->
            val peer = PeerRepository.getPeer(userId)
            if (peer != null) {
                JobCollaborator(
                    userId = userId,
                    userName = peer.userName,
                    role = if (userId == leadCollaborator) CollaboratorRole.LEAD else CollaboratorRole.CONTRIBUTOR
                )
            } else {
                // Create placeholder for unknown users (online-only)
                JobCollaborator(
                    userId = userId,
                    userName = userId, // Use ID as name for now
                    role = if (userId == leadCollaborator) CollaboratorRole.LEAD else CollaboratorRole.CONTRIBUTOR
                )
            }
        }

        val sharedJob = SharedJob(
            id = "shared_${UUID.randomUUID()}",
            baseJob = baseJob,
            collaborators = collaboratorDetails,
            leadCollaborator = leadCollaborator,
            collaborationMode = collaborationMode
        )

        // Add to repository
        _sharedJobs.update { it + sharedJob }
        saveSharedJobs()

        return sharedJob
    }

    /**
     * Get shared job by ID
     */
    fun getSharedJob(jobId: String): SharedJob? {
        return _sharedJobs.value.find { it.id == jobId }
    }

    /**
     * Get shared jobs for a specific user
     */
    fun getSharedJobsForUser(userId: String): List<SharedJob> {
        return _sharedJobs.value.filter { job ->
            job.collaborators.any { it.userId == userId }
        }
    }

    /**
     * Submit report for a shared job
     */
    fun submitReport(jobId: String, collaboratorId: String, report: JobReport): Boolean {
        val job = getSharedJob(jobId) ?: return false

        // Update job with new report
        val updatedJob = job.copy(
            individualReports = job.individualReports + report
        )

        // Update collaborator status
        val updatedCollaborators = job.collaborators.map { collaborator ->
            if (collaborator.userId == collaboratorId) {
                collaborator.copy(status = CollaboratorStatus.SUBMITTED)
            } else collaborator
        }

        val finalJob = updatedJob.copy(collaborators = updatedCollaborators)

        // Update repository
        _sharedJobs.update { jobs ->
            jobs.map { if (it.id == jobId) finalJob else it }
        }
        saveSharedJobs()

        // Check if all collaborators have submitted
        checkAllReportsSubmitted(finalJob)

        return true
    }

    /**
     * Submit sign-off for a shared job
     */
    fun submitSignOff(jobId: String, collaboratorId: String, approved: Boolean, comments: String = ""): Boolean {
        val job = getSharedJob(jobId) ?: return false

        val signOff = JobSignOff(
            collaboratorId = collaboratorId,
            approved = approved,
            comments = comments
        )

        // Update job with sign-off
        val updatedJob = job.copy(
            signOffs = job.signOffs + signOff
        )

        // Update collaborator status
        val updatedCollaborators = job.collaborators.map { collaborator ->
            if (collaborator.userId == collaboratorId) {
                collaborator.copy(status = CollaboratorStatus.SIGNED_OFF)
            } else collaborator
        }

        val finalJob = updatedJob.copy(collaborators = updatedCollaborators)

        // Update repository
        _sharedJobs.update { jobs ->
            jobs.map { if (it.id == jobId) finalJob else it }
        }
        saveSharedJobs()

        // Check if all collaborators have signed off
        checkAllSignedOff(finalJob)

        return true
    }

    /**
     * Mark shared job as completed
     */
    fun completeSharedJob(jobId: String): Boolean {
        val job = getSharedJob(jobId) ?: return false

        val updatedJob = job.copy(
            status = SharedJobStatus.COMPLETED,
            completedAt = System.currentTimeMillis()
        )

        _sharedJobs.update { jobs ->
            jobs.map { if (it.id == jobId) updatedJob else it }
        }
        saveSharedJobs()

        return true
    }

    /**
     * Check if all collaborators have submitted reports
     */
    private fun checkAllReportsSubmitted(job: SharedJob) {
        val expectedSubmissions = job.collaborators.count { it.role != CollaboratorRole.REVIEWER }
        val actualSubmissions = job.individualReports.size

        if (actualSubmissions >= expectedSubmissions) {
            val updatedJob = job.copy(status = SharedJobStatus.REPORTS_SUBMITTED)
            _sharedJobs.update { jobs ->
                jobs.map { if (it.id == job.id) updatedJob else it }
            }
            saveSharedJobs()
        }
    }

    /**
     * Check if all collaborators have signed off
     */
    private fun checkAllSignedOff(job: SharedJob) {
        val expectedSignOffs = job.collaborators.size
        val actualSignOffs = job.signOffs.size
        val allApproved = job.signOffs.all { it.approved }

        if (actualSignOffs >= expectedSignOffs && allApproved) {
            val updatedJob = job.copy(status = SharedJobStatus.SIGNED_OFF)
            _sharedJobs.update { jobs ->
                jobs.map { if (it.id == job.id) updatedJob else it }
            }
            saveSharedJobs()
        }
    }

    /**
     * Save shared jobs to persistent storage
     */
    private fun saveSharedJobs() {
        val jobsJson = JSONArray()
        _sharedJobs.value.forEach { job ->
            jobsJson.put(job.toJson())
        }
        prefs?.edit()?.putString(KEY_SHARED_JOBS, jobsJson.toString())?.apply()
    }

    /**
     * Load shared jobs from persistent storage
     */
    private fun loadSharedJobs() {
        val jobsJson = prefs?.getString(KEY_SHARED_JOBS, null) ?: return

        try {
            val jobsArray = JSONArray(jobsJson)
            val jobs = mutableListOf<SharedJob>()

            for (i in 0 until jobsArray.length()) {
                val jobJson = jobsArray.getJSONObject(i)
                val job = SharedJob.fromJson(jobJson)
                if (job != null) {
                    jobs.add(job)
                }
            }

            _sharedJobs.value = jobs
        } catch (e: Exception) {
            // Handle parsing errors
        }
    }

    /**
     * Convert SharedJob to JSON for storage
     */
    private fun SharedJob.toJson(): JSONObject {
        return JSONObject().apply {
            put("id", id)
            put("baseJobId", baseJob.id)
            put("collaborators", JSONArray(collaborators.map { it.toJson() }))
            put("leadCollaborator", leadCollaborator)
            put("collaborationMode", collaborationMode.name)
            put("status", status.name)
            put("individualReports", JSONArray(individualReports.map { it.toJson() }))
            put("signOffs", JSONArray(signOffs.map { it.toJson() }))
            put("sharedAt", sharedAt)
            put("completedAt", completedAt)
            put("invoicedAt", invoicedAt)
        }
    }

    /**
     * Create SharedJob from JSON
     */
    private fun SharedJob.Companion.fromJson(json: JSONObject): SharedJob? {
        return try {
            val baseJobId = json.getString("baseJobId")
            val baseJob = JobRepository.getJob(baseJobId) ?: return null

            SharedJob(
                id = json.getString("id"),
                baseJob = baseJob,
                collaborators = json.getJSONArray("collaborators")?.let { array ->
                    (0 until array.length()).mapNotNull { i ->
                        JobCollaborator.fromJson(array.getJSONObject(i))
                    }
                } ?: emptyList(),
                leadCollaborator = json.getString("leadCollaborator"),
                collaborationMode = CollaborationMode.valueOf(json.optString("collaborationMode", "TEAM_EFFORT")),
                status = SharedJobStatus.valueOf(json.optString("status", "ACTIVE")),
                individualReports = json.optJSONArray("individualReports")?.let { array ->
                    (0 until array.length()).mapNotNull { i ->
                        JobReport.fromJson(array.getJSONObject(i))
                    }
                } ?: emptyList(),
                signOffs = json.optJSONArray("signOffs")?.let { array ->
                    (0 until array.length()).mapNotNull { i ->
                        JobSignOff.fromJson(array.getJSONObject(i))
                    }
                } ?: emptyList(),
                sharedAt = json.optLong("sharedAt", System.currentTimeMillis()),
                completedAt = json.optLong("completedAt", -1L).takeIf { it > 0 },
                invoicedAt = json.optLong("invoicedAt", -1L).takeIf { it > 0 }
            )
        } catch (e: Exception) {
            null
        }
    }

    // JSON conversion helpers
    private fun JobCollaborator.toJson() = JSONObject().apply {
        put("userId", userId)
        put("userName", userName)
        put("role", role.name)
        put("joinedAt", joinedAt)
        put("status", status.name)
    }

    private fun JobCollaborator.Companion.fromJson(json: JSONObject) = JobCollaborator(
        userId = json.getString("userId"),
        userName = json.getString("userName"),
        role = CollaboratorRole.valueOf(json.optString("role", "CONTRIBUTOR")),
        joinedAt = json.optLong("joinedAt", System.currentTimeMillis()),
        status = CollaboratorStatus.valueOf(json.optString("status", "ACTIVE"))
    )

    private fun JobReport.toJson() = JSONObject().apply {
        put("collaboratorId", collaboratorId)
        put("reportType", reportType.name)
        put("content", content)
        put("submittedAt", submittedAt)
        put("attachments", JSONArray(attachments))
    }

    private fun JobReport.Companion.fromJson(json: JSONObject) = JobReport(
        collaboratorId = json.getString("collaboratorId"),
        reportType = ReportType.valueOf(json.optString("reportType", "WORK_PERFORMED")),
        content = json.getString("content"),
        submittedAt = json.optLong("submittedAt", System.currentTimeMillis()),
        attachments = json.optJSONArray("attachments")?.let { array ->
            (0 until array.length()).map { array.getString(it) }
        } ?: emptyList()
    )

    private fun JobSignOff.toJson() = JSONObject().apply {
        put("collaboratorId", collaboratorId)
        put("approved", approved)
        put("comments", comments)
        put("signedAt", signedAt)
    }

    private fun JobSignOff.Companion.fromJson(json: JSONObject) = JobSignOff(
        collaboratorId = json.getString("collaboratorId"),
        approved = json.getBoolean("approved"),
        comments = json.optString("comments", ""),
        signedAt = json.optLong("signedAt", System.currentTimeMillis())
    )
}