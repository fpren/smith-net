package com.guildofsmiths.trademesh.ai

object SmithAIToolBridge {

    data class JobSnapshot(
        val id: String,
        val title: String,
        val stage: String,
        val clientName: String?,
        val clientPhone: String?,
        val clientAddress: String?,
        val dueDate: Long?,
        val updatedAt: Long
    )

    data class ClientSnapshot(
        val name: String,
        val phone: String,
        val address: String,
        val activeJobCount: Int,
        val totalJobCount: Int
    )

    sealed class CreateJobResult {
        data class Created(val jobId: String, val title: String) : CreateJobResult()
        data class Failed(val reason: String) : CreateJobResult()
    }

    sealed class UpdateStageResult {
        data class Updated(val jobId: String, val newStage: String) : UpdateStageResult()
        data class Failed(val reason: String) : UpdateStageResult()
    }

    @Volatile var jobsSnapshot: () -> List<JobSnapshot> = { emptyList() }
    @Volatile var clientsSnapshot: () -> List<ClientSnapshot> = { emptyList() }
    @Volatile var createJob: (title: String, clientName: String?, address: String?, stage: String?) -> CreateJobResult =
        { _, _, _, _ -> CreateJobResult.Failed("Open the Jobs tab once to enable this action") }
    @Volatile var updateJobStage: (jobId: String, newStage: String) -> UpdateStageResult =
        { _, _ -> UpdateStageResult.Failed("Open the Jobs tab once to enable this action") }

    fun resetForTesting() {
        jobsSnapshot = { emptyList() }
        clientsSnapshot = { emptyList() }
        createJob = { _, _, _, _ -> CreateJobResult.Failed("not initialized") }
        updateJobStage = { _, _ -> UpdateStageResult.Failed("not initialized") }
    }
}
