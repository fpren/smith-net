package com.guildofsmiths.trademesh.e2e

import android.app.Application
import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.guildofsmiths.trademesh.data.ClientRepository
import com.guildofsmiths.trademesh.data.IntentRepository
import com.guildofsmiths.trademesh.data.UserPreferences
import com.guildofsmiths.trademesh.service.AuthService
import com.guildofsmiths.trademesh.ui.dashboard.DashboardViewModel
import com.guildofsmiths.trademesh.ui.expenses.InvoiceBolHtmlRenderer
import com.guildofsmiths.trademesh.ui.expenses.OutputMode
import com.guildofsmiths.trademesh.ui.invoice.InvoiceGenerator
import com.guildofsmiths.trademesh.ui.invoice.InvoiceMode
import com.guildofsmiths.trademesh.ui.invoice.LineItemCategory
import com.guildofsmiths.trademesh.ui.jobboard.Job
import com.guildofsmiths.trademesh.ui.jobboard.JobBoardViewModel
import com.guildofsmiths.trademesh.ui.jobboard.JobStage
import com.guildofsmiths.trademesh.ui.jobboard.JobStatus
import com.guildofsmiths.trademesh.ui.jobboard.Material
import com.guildofsmiths.trademesh.ui.jobboard.TaskStatus
import com.guildofsmiths.trademesh.ui.timetracking.TimeEntry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Calendar
import java.util.UUID

/**
 * Solo Mode end-to-end pipeline test: Plan -> Job -> Tasks -> Invoice.
 *
 * WHY THIS IS A "DATA-PIPELINE" E2E (not widget-driven): the app ships zero
 * Compose testTag/semantics identifiers, so there is no stable way to drive the
 * real screens by widget. Instead this test drives the EXACT production functions
 * the UI calls -- AuthService, ClientRepository, IntentRepository,
 * JobBoardViewModel (createJob/selectJob/startTask/completeTask/addMaterial/
 * updateMaterialCost/moveJobStage), and InvoiceGenerator -- against the real Room
 * DB + real backend session. The invoice asserted at the end is generated from the
 * very Job the ViewModel produced through those mutators. SoloLaunchSmokeTest
 * separately covers "app launches without crash".
 *
 * Adapted to the current app (see plan): single-profile onboarding (not multi-
 * profile create), no client note field, jobs start at JobStage.LEAD (no
 * "Scheduled" status / calendar entry), invoice is the in-app model (no PDF).
 * Those are recorded gaps, not failures.
 *
 * REQUIRES (real backend session, same as the apk's normal usage and
 * InvoicesPushE2ETest):
 *   1. Dev backend running on the host at :3030.
 *   2. Device/emulator with `adb reverse tcp:3030 tcp:3030` (debug BACKEND_URL is
 *      http://127.0.0.1:3030 -> host via reverse).
 */
@RunWith(AndroidJUnit4::class)
class SoloPipelineE2ETest {

    private val F = SoloE2EFixtures

    private lateinit var ctx: Context
    private lateinit var vm: JobBoardViewModel

    private val instr get() = InstrumentationRegistry.getInstrumentation()

    @Before
    fun setUp() {
        ctx = instr.targetContext
        // Production singletons the flow depends on.
        UserPreferences.init(ctx)
        ClientRepository.init(ctx)
        IntentRepository.init(ctx.getSharedPreferences("trademesh_intents", Context.MODE_PRIVATE))
        AuthService.init(ctx)
        // AndroidViewModel uses viewModelScope (Main dispatcher) in init; build it
        // on the main thread.
        instr.runOnMainSync {
            vm = JobBoardViewModel(ctx.applicationContext as Application)
        }
    }

    @Test
    fun soloFlow_producesInvoiceTotaling1053_95() = runBlocking {
        // ── STEP 0-1: profile / auth (single-profile onboarding model) ──────────
        val email = "solo-e2e@smithnet.test"
        val password = "SoloE2E!2026"
        val login = AuthService.login(email, password)
        if (!login.success) {
            AuthService.register(email, password, F.PROVIDER_NAME)
        }
        assertTrue(
            "Must be logged in. Is the backend running on :3030 and `adb reverse tcp:3030 tcp:3030` set?",
            AuthService.isLoggedIn()
        )
        UserPreferences.setUserName(F.PROVIDER_NAME)
        UserPreferences.setHourlyRate(F.HOURLY_RATE)
        AuthService.syncWorkMode("solo")

        // Let the ViewModel's initial loadJobs() settle before createJob so the
        // async backend merge can't race the local append.
        waitUntil("initial job load to settle") { !vm.isLoading.value }

        // ── STEP 2: client (with note) ───────────────────────────────────────────
        ClientRepository.addManualClient(F.CLIENT_NAME, F.CLIENT_PHONE, F.CLIENT_ADDRESS, note = F.CLIENT_NOTE)
        val clientNames = ClientRepository.getClients(vm.jobs.value).map { it.name }
        assertTrue("Acme Motors should appear in client list", clientNames.contains(F.CLIENT_NAME))
        val savedClient = ClientRepository.getClientOverride(F.CLIENT_NAME)
        assertEquals("Client address saved", F.CLIENT_ADDRESS, savedClient?.address)
        assertEquals("Client note saved", F.CLIENT_NOTE, savedClient?.note)

        // ── STEP 3: plan (Intent/Proposal) ───────────────────────────────────────
        val (_, version) = IntentRepository.createIntent(
            scopeStatement = F.SCOPE,
            parties = listOf(F.CLIENT_NAME),
            createdBy = UserPreferences.getUserId(),
            taskDescriptions = F.TASKS,
            crewSize = 1,
        )
        assertEquals("Plan must carry all 6 tasks in order", F.TASKS, version.taskDescriptions)

        // ── STEP 4: convert plan -> job (scheduled at 9:00 AM today) ─────────────
        // Unique title so we can find exactly this job regardless of any persisted
        // or backend-merged jobs.
        val jobTitle = "${F.JOB_TITLE_BASE} ${System.nanoTime()}"
        val startAt9am = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 9); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val startDateMs = startAt9am.timeInMillis
        instr.runOnMainSync {
            vm.createJob(
                title = jobTitle,
                clientName = F.CLIENT_NAME,
                clientPhone = F.CLIENT_PHONE,
                clientAddress = F.CLIENT_ADDRESS,
                hourlyRate = F.HOURLY_RATE,
                estimatedStartDate = startDateMs,
                crewSize = 1,
                taskDescriptions = F.TASKS,
                proposalId = version.id,
                status = JobStatus.SCHEDULED,
            )
        }
        var job = waitForJob(jobTitle)
        assertEquals("Job created from plan is SCHEDULED", JobStatus.SCHEDULED, job.status)
        assertEquals("Scheduled start date recorded", startDateMs, job.estimatedStartDate)

        // Calendar entry: the scheduled job must surface on the dashboard month
        // calendar (which groups by estimatedStartDate). Assert before closing the
        // job, since the dashboard drops CLOSED jobs.
        val dash = DashboardViewModel()
        dash.loadJobs(vm.jobs.value)
        val dayOfMonth = startAt9am.get(Calendar.DAY_OF_MONTH)
        assertTrue("Scheduled day appears on calendar", dash.getScheduledDays().contains(dayOfMonth))
        assertTrue(
            "Job appears in the calendar entry for its start day",
            dash.getJobsForDay(dayOfMonth).any { it.id == job.id }
        )

        // Tasks must be seeded from the plan (the carryover failure-condition).
        instr.runOnMainSync { vm.selectJob(job) }
        waitUntil("6 tasks seeded for the job") {
            vm.tasks.value.size == F.TASKS.size && vm.tasks.value.all { it.jobId == job.id }
        }
        val seeded = vm.tasks.value.sortedBy { it.order }
        assertEquals("Seeded task titles in order", F.TASKS, seeded.map { it.title })
        assertTrue("All seeded tasks start PENDING", seeded.all { it.status == TaskStatus.PENDING })

        // ── STEP 5a: execute tasks (start -> complete) ───────────────────────────
        for (t in seeded) {
            instr.runOnMainSync { vm.startTask(t.id) }
            instr.runOnMainSync { vm.completeTask(t.id) }
        }
        waitUntil("all 6 tasks DONE") {
            vm.tasks.value.size == F.TASKS.size && vm.tasks.value.all { it.status == TaskStatus.DONE }
        }

        // ── STEP 5b: purchased materials (add + cost so they become line items) ──
        F.MATERIALS.forEachIndexed { i, m ->
            instr.runOnMainSync { vm.addMaterial(job.id, Material(name = m.name)) }
            instr.runOnMainSync {
                vm.updateMaterialCost(
                    jobId = job.id,
                    materialIndex = i,
                    quantity = 1.0,
                    unit = "ea",
                    unitCost = m.price,
                    totalCost = m.price,
                    vendor = m.source,
                )
            }
        }
        job = currentJob(job.id)
        assertEquals("5 materials on the job", 5, job.materials.size)
        assertTrue("All materials checked/purchased", job.materials.all { it.checked })
        assertEquals(
            "Materials subtotal",
            F.MATERIALS_TOTAL, job.materials.sumOf { it.totalCost }, 0.001
        )

        // ── STEP 6: end job (advance lifecycle to CLOSED) ────────────────────────
        instr.runOnMainSync { vm.moveJobStage(job.id, JobStage.REVIEW) }
        instr.runOnMainSync { vm.moveJobStage(job.id, JobStage.INVOICE) }
        instr.runOnMainSync { vm.moveJobStage(job.id, JobStage.CLOSED) }
        job = currentJob(job.id)
        assertEquals("Job closed", JobStage.CLOSED, job.stage)

        // ── STEP 7: generate + verify invoice ────────────────────────────────────
        // Labor comes from a single 8h time entry (clockOut set, REGULAR type).
        // travelRate=0.0 zeroes the auto-added travel line; taxRate=0.0 matches the
        // spec's no-tax total.
        val now = System.currentTimeMillis()
        val laborEntry = TimeEntry(
            id = UUID.randomUUID().toString(),
            userId = UserPreferences.getUserId(),
            userName = F.PROVIDER_NAME,
            clockInTime = now - F.LABOR_MINUTES * 60_000L,
            clockOutTime = now,
            durationMinutes = F.LABOR_MINUTES,
            jobId = job.id,
            jobTitle = job.title,
            createdAt = now,
            immutableHash = "e2e-labor",
        )
        val invoice = InvoiceGenerator.generateFromJob(
            job = job,
            timeEntries = listOf(laborEntry),
            providerName = F.PROVIDER_NAME,
            providerTrade = F.PROVIDER_TRADE,
            hourlyRate = F.HOURLY_RATE,
            travelRate = 0.0,
            taxRate = 0.0,
        )

        assertEquals("Solo mode invoice", InvoiceMode.SOLO, invoice.mode)
        assertEquals("Client (To)", F.CLIENT_NAME, invoice.toName)
        assertEquals("Client address on invoice", F.CLIENT_ADDRESS, invoice.toAddress)
        assertTrue("Contractor (From)", invoice.fromName.contains(F.PROVIDER_NAME))

        val laborLine = invoice.lineItems.single { it.category == LineItemCategory.LABOR }
        assertEquals("Labor hours", F.LABOR_HOURS, laborLine.quantity, 0.001)
        assertEquals("Labor rate", F.HOURLY_RATE, laborLine.rate, 0.001)
        assertEquals("Labor total = 8 x 95", F.LABOR_TOTAL, laborLine.total, 0.001)

        val materialLines = invoice.lineItems.filter { it.category == LineItemCategory.MATERIALS }
        assertEquals("5 material line items", 5, materialLines.size)
        assertEquals(
            "Materials line-item subtotal",
            F.MATERIALS_TOTAL, materialLines.sumOf { it.total }, 0.001
        )

        assertEquals("Invoice total", F.INVOICE_TOTAL, invoice.totalDue, 0.001)

        // Invoice can be exported: the PDF/share path renders the invoice to HTML
        // (InvoiceBolHtmlRenderer feeds the WebView->PrintManager PDF export). Assert
        // the rendered document carries the client, the now-populated address, and
        // the invoice number.
        val html = InvoiceBolHtmlRenderer.render(
            invoice = invoice,
            job = job,
            timeEntries = listOf(laborEntry),
            mode = OutputMode.INVOICE_ONLY,
        )
        assertTrue("Export HTML contains client", html.contains(F.CLIENT_NAME))
        assertTrue("Export HTML contains client address", html.contains("1123 East 57th Street"))
        assertTrue("Export HTML contains invoice number", html.contains(invoice.invoiceNumber))
    }

    // ── helpers ──────────────────────────────────────────────────────────────────

    private fun currentJob(id: String): Job =
        vm.jobs.value.first { it.id == id }

    private fun waitForJob(title: String): Job {
        waitUntil("job '$title' present") { vm.jobs.value.any { it.title == title } }
        return vm.jobs.value.first { it.title == title }
    }

    /** Poll [condition] until true or timeout (default 8s). Fails the test on timeout. */
    private fun waitUntil(what: String, timeoutMs: Long = 8_000, predicate: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (predicate()) return
            Thread.sleep(50)
        }
        if (!predicate()) throw AssertionError("Timed out after ${timeoutMs}ms waiting for: $what")
    }
}
