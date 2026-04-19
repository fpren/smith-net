package com.guildofsmiths.trademesh.ui.map

import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import com.guildofsmiths.trademesh.data.ClockStatus
import com.guildofsmiths.trademesh.data.CrewPresenceInfo
import com.guildofsmiths.trademesh.data.CrewPresenceRepository
import com.guildofsmiths.trademesh.ui.ConsoleHeader
import com.guildofsmiths.trademesh.ui.ConsoleTheme
import com.guildofsmiths.trademesh.ui.dashboard.CrewProfileSheet
import com.guildofsmiths.trademesh.ui.jobboard.Job
import com.guildofsmiths.trademesh.ui.jobboard.JobBoardViewModel
import com.guildofsmiths.trademesh.ui.jobboard.JobStage

// Mock coordinates for demo sites
private val SITE_COORDS = mapOf(
    "847 Flatbush Ave, Brooklyn NY" to GeoPoint(40.6505, -73.9612),
    "55 W 125th St, Apt 4B, Manhattan NY" to GeoPoint(40.8088, -73.9442),
    "1220 Ocean Pkwy, Brooklyn NY" to GeoPoint(40.6275, -73.9685),
)

@Composable
fun MapScreen(
    onBack: () -> Unit,
    onJobClick: ((String) -> Unit)? = null,
    onCallPhone: ((String) -> Unit)? = null,
    onMessageCrew: ((CrewPresenceInfo) -> Unit)? = null,
    jobViewModel: JobBoardViewModel = viewModel()
) {
    val context = LocalContext.current
    val crew by CrewPresenceRepository.crew.collectAsState()
    val allJobs by jobViewModel.jobs.collectAsState()
    val activeJobs = allJobs.filter { it.stage != JobStage.CLOSED }

    val bySite = remember(crew) {
        crew.filter { it.currentSite != null }.groupBy { it.currentSite!! }
    }
    val activeCount = crew.count { it.status == ClockStatus.ON_CLOCK }

    var selectedCrew by remember { mutableStateOf<CrewPresenceInfo?>(null) }

    // Crew profile sheet
    if (selectedCrew != null) {
        CrewProfileSheet(
            member = selectedCrew!!,
            onDismiss = { selectedCrew = null },
            onCall = { phone -> onCallPhone?.invoke(phone) },
            onMessage = onMessageCrew
        )
    }

    // Init osmdroid
    LaunchedEffect(Unit) {
        Configuration.getInstance().userAgentValue = context.packageName
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ConsoleTheme.background)
    ) {
        ConsoleHeader(title = "MAP", onBackClick = onBack)

        // Summary bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(ConsoleTheme.surface)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                "$activeCount/${crew.size} on clock",
                style = ConsoleTheme.bodySmall.copy(color = ConsoleTheme.accent)
            )
            Text(
                "${bySite.size} sites · ${activeJobs.size} active jobs",
                style = ConsoleTheme.bodySmall.copy(color = ConsoleTheme.textMuted)
            )
        }

        // ── OPENSTREETMAP ────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp)
                .border(0.5.dp, ConsoleTheme.text.copy(alpha = 0.06f))
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    MapView(ctx).apply {
                        setTileSource(TileSourceFactory.MAPNIK)
                        setMultiTouchControls(true)
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        controller.setZoom(12.0)
                        controller.setCenter(GeoPoint(40.7128, -73.9560))
                    }
                },
                update = { mapView ->
                    mapView.overlays.clear()

                    // Place markers for sites with crew
                    bySite.forEach { (site, members) ->
                        val coords = SITE_COORDS[site] ?: return@forEach
                        val activeOnSite = members.count { it.status == ClockStatus.ON_CLOCK }
                        val names = members.joinToString(", ") { it.name }
                        val jobTitle = members.firstOrNull()?.currentJobTitle ?: "Job site"

                        val marker = Marker(mapView).apply {
                            position = coords
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                            title = jobTitle
                            snippet = "$names ($activeOnSite/${members.size} on site)"
                        }
                        mapView.overlays.add(marker)
                    }

                    // Place markers for jobs without crew on site (from job addresses)
                    activeJobs.forEach { job ->
                        val addr = job.clientAddress
                        if (addr.isNotBlank() && !bySite.containsKey(addr)) {
                            val coords = SITE_COORDS[addr] ?: return@forEach
                            val marker = Marker(mapView).apply {
                                position = coords
                                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                                title = job.clientName ?: job.title
                                snippet = "${job.stage.displayName} · No crew on site"
                            }
                            mapView.overlays.add(marker)
                        }
                    }

                    mapView.invalidate()
                }
            )
        }

        // ── SITE LIST (below map) ────────────────────────
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Active job sites with crew
            bySite.forEach { (site, members) ->
                val siteActive = members.count { it.status == ClockStatus.ON_CLOCK }
                val jobTitle = members.firstOrNull()?.currentJobTitle
                val jobId = members.firstOrNull()?.currentJobId

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(ConsoleTheme.surface, RoundedCornerShape(4.dp))
                        .border(0.5.dp, ConsoleTheme.text.copy(alpha = 0.06f), RoundedCornerShape(4.dp))
                        .clip(RoundedCornerShape(4.dp))
                        .then(
                            if (jobId != null && onJobClick != null)
                                Modifier.clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = rememberRipple(bounded = true),
                                    onClick = { onJobClick(jobId) }
                                )
                            else Modifier
                        )
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Site header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            if (jobTitle != null) {
                                Text(jobTitle, style = ConsoleTheme.bodySmall.copy(color = ConsoleTheme.text))
                            }
                            Text(site, style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            members.forEach { m ->
                                val dotColor = when (m.status) {
                                    ClockStatus.ON_CLOCK -> ConsoleTheme.success
                                    ClockStatus.ON_BREAK -> ConsoleTheme.accent
                                    ClockStatus.OFF_CLOCK -> ConsoleTheme.textMuted
                                }
                                Text("●", style = ConsoleTheme.caption.copy(color = dotColor))
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("$siteActive/${members.size}", style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted))
                        }
                    }

                    Box(Modifier.fillMaxWidth().height(0.5.dp).background(ConsoleTheme.text.copy(alpha = 0.06f)))

                    // Crew at site — tappable for profiles
                    members.forEach { member ->
                        val dotColor = when (member.status) {
                            ClockStatus.ON_CLOCK -> ConsoleTheme.success
                            ClockStatus.ON_BREAK -> ConsoleTheme.accent
                            ClockStatus.OFF_CLOCK -> ConsoleTheme.textMuted
                        }
                        val hoursText = if (member.clockInTime != null) {
                            val mins = (System.currentTimeMillis() - member.clockInTime) / 60_000
                            "${mins / 60}h ${mins % 60}m"
                        } else "--"

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(4.dp))
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = rememberRipple(bounded = true),
                                    onClick = { selectedCrew = member }
                                )
                                .padding(vertical = 3.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text("(●)", style = ConsoleTheme.caption.copy(color = dotColor))
                                Text(
                                    "${member.name} · ${member.trade}",
                                    style = ConsoleTheme.bodySmall.copy(color = ConsoleTheme.text)
                                )
                            }
                            Text(hoursText, style = ConsoleTheme.caption.copy(color = ConsoleTheme.accent))
                        }
                    }
                }
            }

            // Jobs not on any site (no crew present)
            val jobsWithoutCrew = activeJobs.filter { job ->
                job.clientAddress.isNotBlank() && !bySite.containsKey(job.clientAddress)
            }
            if (jobsWithoutCrew.isNotEmpty()) {
                Text("OTHER JOBS", style = ConsoleTheme.captionBold.copy(color = ConsoleTheme.textMuted))
                jobsWithoutCrew.forEach { job ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(ConsoleTheme.surface, RoundedCornerShape(4.dp))
                            .border(0.5.dp, ConsoleTheme.text.copy(alpha = 0.06f), RoundedCornerShape(4.dp))
                            .clip(RoundedCornerShape(4.dp))
                            .then(
                                if (onJobClick != null)
                                    Modifier.clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = rememberRipple(bounded = true),
                                        onClick = { onJobClick(job.id) }
                                    )
                                else Modifier
                            )
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                job.clientName ?: job.title,
                                style = ConsoleTheme.bodySmall.copy(color = ConsoleTheme.text)
                            )
                            Text(
                                "${job.stage.displayName} · ${job.clientAddress.take(30)}",
                                style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted)
                            )
                            if (job.crew.isNotEmpty()) {
                                Text(
                                    "Assigned: ${job.crew.joinToString(", ") { it.name }}",
                                    style = ConsoleTheme.caption.copy(color = ConsoleTheme.success)
                                )
                            } else {
                                Text("No crew assigned", style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted))
                            }
                        }
                        Text(">", style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted))
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
