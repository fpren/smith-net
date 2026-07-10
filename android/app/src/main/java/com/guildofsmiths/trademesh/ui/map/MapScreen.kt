package com.guildofsmiths.trademesh.ui.map

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.guildofsmiths.trademesh.data.CrewPresenceInfo
import com.guildofsmiths.trademesh.data.CrewPresenceRepository
import com.guildofsmiths.trademesh.ui.Tokens2
import com.guildofsmiths.trademesh.ui.dashboard.CrewMapView
import com.guildofsmiths.trademesh.ui.dashboard.CrewProfileSheet
import com.guildofsmiths.trademesh.ui.jobboard.Job
import com.guildofsmiths.trademesh.ui.jobboard.JobBoardViewModel
import com.guildofsmiths.trademesh.ui.jobboard.JobStage
import com.guildofsmiths.trademesh.ui.theme2.LocalSmithColors
import com.guildofsmiths.trademesh.ui.theme2.SmithType

@Composable
fun MapScreen(
    onBack: () -> Unit,
    onJobClick: ((String) -> Unit)? = null,
    onCallPhone: ((String) -> Unit)? = null,
    onMessageCrew: ((CrewPresenceInfo) -> Unit)? = null,
    jobViewModel: JobBoardViewModel = viewModel()
) {
    val colors = LocalSmithColors.current
    val crew by CrewPresenceRepository.crew.collectAsState()
    val allJobs by jobViewModel.jobs.collectAsState()
    // Geocoding is async on the backend — re-pull on map open so coords for
    // recently created jobs land without an app restart.
    LaunchedEffect(Unit) { jobViewModel.refreshFromBackend() }
    val activeJobs = allJobs.filter { it.stage != JobStage.CLOSED }
    val bySite = remember(crew) {
        crew.filter { it.currentSite != null }.groupBy { it.currentSite!! }
    }

    var selectedSite by remember { mutableStateOf<String?>(null) }
    var selectedJob by remember { mutableStateOf<Job?>(null) }
    var selectedCrew by remember { mutableStateOf<CrewPresenceInfo?>(null) }

    Box(modifier = Modifier.fillMaxSize().background(colors.bgBase)) {
        // Layer 1: full-bleed map
        CrewMapView(
            crew = crew,
            activeJobs = activeJobs,
            fillContainer = true,
            onSiteClick = { addr ->
                selectedSite = addr
                selectedJob = null
            },
            onJobClick = { id ->
                selectedJob = activeJobs.firstOrNull { it.id == id }
                selectedSite = null
            }
        )

        // Layer 2: floating back chip — top-left, semi-transparent.
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(9.dp)
                .background(colors.bgPanel.copy(alpha = 0.85f), RoundedCornerShape(Tokens2.RadiusOps))
                .border(1.dp, colors.line, RoundedCornerShape(Tokens2.RadiusOps))
                .clickable(onClick = onBack)
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Text("[← BACK]", style = SmithType.caption.copy(color = colors.accent))
        }

        // Layer 3: contextual panels — appear when a marker is tapped.
        selectedSite?.let { site ->
            SiteDetailPanel(
                modifier = Modifier.align(Alignment.BottomCenter),
                site = site,
                members = bySite[site].orEmpty(),
                onCrewTap = { selectedCrew = it },
                onClose = { selectedSite = null }
            )
        }
        selectedJob?.let { job ->
            JobDetailPanel(
                modifier = Modifier.align(Alignment.BottomCenter),
                job = job,
                onJumpToJob = {
                    selectedJob = null
                    onJobClick?.invoke(job.id)
                },
                onClose = { selectedJob = null }
            )
        }

        // Layer 4: CrewProfileSheet on top of everything.
        if (selectedCrew != null) {
            CrewProfileSheet(
                member = selectedCrew!!,
                onDismiss = { selectedCrew = null },
                onCall = { phone -> onCallPhone?.invoke(phone) },
                onMessage = onMessageCrew
            )
        }
    }
}

