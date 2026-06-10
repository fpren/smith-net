package com.guildofsmiths.trademesh.data

import android.util.Log
import com.guildofsmiths.trademesh.BuildConfig
import com.guildofsmiths.trademesh.ui.jobboard.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Requests a rendered per-job report from the Hetzner backend
 * (POST /api/reports/job?format=pdf|xlsx) and returns the file bytes. The client
 * owns the data, so it builds a denormalized payload from the Job + labor
 * minutes; the server (jobReport.ts) renders the PDF/Excel.
 */
object JobReportClient {

    private const val TAG = "JobReportClient"
    private val JSON = "application/json; charset=utf-8".toMediaType()

    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    /** format is "pdf" or "xlsx". Returns the rendered bytes, or null on failure. */
    suspend fun render(
        job: Job,
        laborMinutes: Int,
        contractorName: String?,
        workSummary: String?,
        periodLabel: String?,
        format: String
    ): ByteArray? = withContext(Dispatchers.IO) {
        val payload = buildPayload(job, laborMinutes, contractorName, workSummary, periodLabel)
        val host = BuildConfig.BACKEND_URL_PRIMARY.trimEnd('/')
        try {
            val builder = Request.Builder()
                .url("$host/api/reports/job?format=$format")
                .post(payload.toString().toRequestBody(JSON))
            SupabaseAuth.getAccessToken()?.let { builder.header("Authorization", "Bearer $it") }
            http.newCall(builder.build()).execute().use { resp ->
                if (!resp.isSuccessful) {
                    Log.w(TAG, "render($format) -> ${resp.code}")
                    return@withContext null
                }
                resp.body?.bytes()
            }
        } catch (e: Exception) {
            Log.w(TAG, "render($format) failed: ${e.message}")
            null
        }
    }

    private fun buildPayload(
        job: Job,
        laborMinutes: Int,
        contractorName: String?,
        workSummary: String?,
        periodLabel: String?
    ): JSONObject {
        val laborHours = laborMinutes / 60.0
        val laborRate = job.hourlyRate
        val laborCost = laborHours * laborRate

        val materials = JSONArray()
        var materialsCost = 0.0
        for (m in job.materials) {
            val total = m.quantity * m.unitCost
            materialsCost += total
            materials.put(JSONObject().apply {
                put("name", m.name)
                put("quantity", m.quantity)
                put("unit", m.unit)
                put("unitCost", m.unitCost)
                put("total", total)
            })
        }

        val expenses = JSONArray()
        var expensesTotal = 0.0
        for (e in job.expenses) {
            val amount = e.quantity * e.unitCost
            expensesTotal += amount
            expenses.put(JSONObject().apply {
                put("category", e.category)
                put("description", e.description)
                put("amount", amount)
                put("vendor", e.vendor)
            })
        }

        val total = laborCost + materialsCost + expensesTotal

        return JSONObject().apply {
            put("jobTitle", job.title)
            put("clientName", job.clientName ?: "")
            put("contractorName", contractorName ?: "")
            put("workSummary", workSummary ?: job.description)
            periodLabel?.let { put("periodLabel", it) }
            put("laborHours", laborHours)
            put("laborRate", laborRate)
            put("laborCost", laborCost)
            put("materials", materials)
            put("materialsCost", materialsCost)
            put("expenses", expenses)
            put("expensesTotal", expensesTotal)
            put("total", total)
            put("generatedAtMs", System.currentTimeMillis())
        }
    }
}
