package com.guildofsmiths.trademesh.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.guildofsmiths.trademesh.data.SharedJob
import com.guildofsmiths.trademesh.data.SharedJobRepository
import com.guildofsmiths.trademesh.data.SharedJobStatus
import com.guildofsmiths.trademesh.data.UserPreferences

/**
 * Shared Jobs Screen
 * Shows collaborative jobs user is participating in
 */
@Composable
fun SharedJobsScreen(
    onBackClick: () -> Unit,
    onSharedJobClick: (SharedJob) -> Unit,
    modifier: Modifier = Modifier
) {
    val currentUserId = UserPreferences.getUserId()
    val sharedJobs by SharedJobRepository.sharedJobs.collectAsState()

    // Filter to show only jobs this user is participating in
    val userSharedJobs = sharedJobs.filter { job ->
        job.collaborators.any { it.userId == currentUserId }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ConsoleTheme.background)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(ConsoleTheme.surface)
                .clickable(onClick = onBackClick)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "←", style = ConsoleTheme.title)
            Spacer(modifier = Modifier.width(14.dp))
            Text(text = "SHARED JOBS", style = ConsoleTheme.title)
        }

        ConsoleSeparator()

        if (userSharedJobs.isEmpty()) {
            // Empty state
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "No shared jobs yet",
                    style = ConsoleTheme.body
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Jobs shared with you will appear here",
                    style = ConsoleTheme.caption
                )
            }
        } else {
            // Shared jobs list
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(userSharedJobs.sortedByDescending { it.sharedAt }) { sharedJob ->
                    SharedJobCard(
                        sharedJob = sharedJob,
                        currentUserId = currentUserId,
                        onClick = { onSharedJobClick(sharedJob) }
                    )
                }
            }
        }
    }
}

/**
 * Individual shared job card
 */
@Composable
private fun SharedJobCard(
    sharedJob: SharedJob,
    currentUserId: String,
    onClick: () -> Unit
) {
    val collaborator = sharedJob.collaborators.find { it.userId == currentUserId }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(ConsoleTheme.surface)
            .clickable(onClick = onClick)
            .padding(16.dp)
    ) {
        // Job title and status
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(sharedJob.baseJob.title, style = ConsoleTheme.bodyBold)
                Text(
                    "Shared by ${sharedJob.collaborators.find { it.userId == sharedJob.leadCollaborator }?.userName ?: "Unknown"}",
                    style = ConsoleTheme.caption
                )
            }

            // Status indicator
            SharedJobStatusIndicator(sharedJob.status)
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Collaboration info
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Collaborator count
            Text(
                "${sharedJob.collaborators.size} collaborators",
                style = ConsoleTheme.caption
            )

            // User's status in this job
            collaborator?.let { collab ->
                CollaboratorStatusBadge(collab)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Progress indicators
        CollaborationProgress(sharedJob, currentUserId)

        // Action hints based on status
        Spacer(modifier = Modifier.height(8.dp))
        StatusActionHint(sharedJob, collaborator)
    }
}

/**
 * Status indicator for shared job
 */
@Composable
private fun SharedJobStatusIndicator(status: SharedJobStatus) {
    val (color, text) = when (status) {
        SharedJobStatus.ACTIVE -> Color.Blue to "ACTIVE"
        SharedJobStatus.REPORTS_PENDING -> Color.Yellow to "PENDING"
        SharedJobStatus.REPORTS_SUBMITTED -> ConsoleTheme.accent to "REVIEW"
        SharedJobStatus.SIGN_OFF_PENDING -> Color.Orange to "SIGN-OFF"
        SharedJobStatus.SIGNED_OFF -> Color.Green to "APPROVED"
        SharedJobStatus.COMPLETED -> Color.Green to "DONE"
        SharedJobStatus.INVOICED -> ConsoleTheme.success to "BILLED"
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .background(color.copy(alpha = 0.1f))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(text, style = ConsoleTheme.captionBold, color = color)
    }
}

/**
 * User's status badge in this collaboration
 */
@Composable
private fun CollaboratorStatusBadge(collaborator: com.guildofsmiths.trademesh.data.JobCollaborator) {
    val (color, text) = when (collaborator.status) {
        com.guildofsmiths.trademesh.data.CollaboratorStatus.INVITED -> Color.Gray to "INVITED"
        com.guildofsmiths.trademesh.data.CollaboratorStatus.ACTIVE -> Color.Blue to "ACTIVE"
        com.guildofsmiths.trademesh.data.CollaboratorStatus.SUBMITTED -> ConsoleTheme.accent to "REPORTED"
        com.guildofsmiths.trademesh.data.CollaboratorStatus.SIGNED_OFF -> Color.Green to "APPROVED"
        com.guildofsmiths.trademesh.data.CollaboratorStatus.DROPPED_OUT -> Color.Red to "DROPPED"
    }

    Text(
        text,
        style = ConsoleTheme.caption,
        color = color
    )
}

/**
 * Progress indicators for collaboration
 */
@Composable
private fun CollaborationProgress(sharedJob: SharedJob, currentUserId: String) {
    val totalCollaborators = sharedJob.collaborators.size
    val submittedReports = sharedJob.individualReports.size
    val signedOff = sharedJob.signOffs.count { it.approved }
    val userSubmitted = sharedJob.individualReports.any { it.collaboratorId == currentUserId }
    val userSignedOff = sharedJob.signOffs.any { it.collaboratorId == currentUserId }

    Column(modifier = Modifier.fillMaxWidth()) {
        // Reports progress
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Reports: $submittedReports/$totalCollaborators", style = ConsoleTheme.caption)
            if (userSubmitted) {
                Text("✓ You reported", style = ConsoleTheme.caption, color = ConsoleTheme.success)
            } else {
                Text("• Your report needed", style = ConsoleTheme.caption, color = Color.Yellow)
            }
        }

        // Sign-off progress (only show if reports are submitted)
        if (sharedJob.status >= SharedJobStatus.REPORTS_SUBMITTED) {
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Approvals: $signedOff/$totalCollaborators", style = ConsoleTheme.caption)
                if (userSignedOff) {
                    Text("✓ You approved", style = ConsoleTheme.caption, color = ConsoleTheme.success)
                } else {
                    Text("• Your approval needed", style = ConsoleTheme.caption, color = Color.Yellow)
                }
            }
        }
    }
}

/**
 * Action hints based on current status
 */
@Composable
private fun StatusActionHint(
    sharedJob: SharedJob,
    collaborator: com.guildofsmiths.trademesh.data.JobCollaborator?
) {
    val hintText = when {
        collaborator?.status == com.guildofsmiths.trademesh.data.CollaboratorStatus.INVITED ->
            "Tap to accept invitation"
        collaborator?.status == com.guildofsmiths.trademesh.data.CollaboratorStatus.ACTIVE &&
        !sharedJob.individualReports.any { it.collaboratorId == collaborator.userId } ->
            "Tap to submit your report"
        sharedJob.status == SharedJobStatus.REPORTS_SUBMITTED &&
        !sharedJob.signOffs.any { it.collaboratorId == collaborator?.userId } ->
            "Tap to review and approve"
        sharedJob.status == SharedJobStatus.SIGNED_OFF ->
            "Waiting for final invoice"
        sharedJob.status == SharedJobStatus.COMPLETED ->
            "Job completed - invoice ready"
        else -> "Tap to view details"
    }

    Text(
        hintText,
        style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted),
        modifier = Modifier.padding(top = 4.dp)
    )
}