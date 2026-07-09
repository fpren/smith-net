package com.guildofsmiths.trademesh.data

import android.util.Log
import com.guildofsmiths.trademesh.BuildConfig
import com.guildofsmiths.trademesh.service.AuthedRequest
import com.guildofsmiths.trademesh.ui.invoice.Invoice
import com.guildofsmiths.trademesh.ui.proposal.Proposal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Creates public, client-facing share pages on the Hetzner backend and returns
 * their absolute URLs:
 *   POST /api/invoice-links -> { uuid } -> {host}/i/{uuid}
 *   POST /api/proposals     -> { uuid } -> {host}/p/{uuid}
 * Both return null on any failure so callers can fall back to plain-text sharing.
 */
object PublicLinkClient {

    private const val TAG = "PublicLinkClient"
    private val JSON = "application/json; charset=utf-8".toMediaType()

    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private fun host(): String = BuildConfig.BACKEND_URL_PRIMARY.trimEnd('/')

    /** Create a shareable invoice page; returns its absolute URL or null. */
    suspend fun createInvoiceLink(jobId: String, invoice: Invoice, clientNameFallback: String?): String? {
        val body = JSONObject().apply {
            put("jobId", jobId)
            put("contractorName", invoice.fromName)
            put("contractorPhone", invoice.fromPhone)
            put("clientName", invoice.toName.ifBlank { clientNameFallback ?: "" })
            put("clientAddress", invoice.toAddress)
            put("workSummary", invoice.projectRef)
            put("hoursWorked", invoice.totalCrewHours)
            put("totalDue", invoice.totalDue)
        }
        val uuid = postForUuid("/api/invoice-links", body) ?: return null
        return "${host()}/i/$uuid"
    }

    /** Create a shareable proposal page; returns its absolute URL or null. */
    suspend fun createProposalLink(jobId: String, proposal: Proposal): String? {
        val body = JSONObject().apply {
            put("jobId", jobId)
            put("contractorName", proposal.providerName)
            put("contractorPhone", proposal.providerPhone ?: "")
            put("clientName", proposal.clientName)
            put("clientAddress", proposal.clientAddress ?: "")
            put("scope", proposal.scopeStatement)
            put("laborHours", proposal.laborLine.estimatedHours)
            put("laborRate", proposal.laborLine.hourlyRate)
            put("laborCost", proposal.laborLine.total)
            put("materialsCost", proposal.materialLines.sumOf { it.total })
            put("totalCost", proposal.total)
        }
        val uuid = postForUuid("/api/proposals", body) ?: return null
        return "${host()}/p/$uuid"
    }

    private suspend fun postForUuid(path: String, body: JSONObject): String? = withContext(Dispatchers.IO) {
        try {
            val builder = Request.Builder()
                .url("${host()}$path")
                .post(body.toString().toRequestBody(JSON))
            // /api/invoice-links and /api/proposals sit behind authenticateToken.
            SupabaseAuth.getAccessToken()?.let { builder.header("Authorization", "Bearer $it") }
            val req = builder.build()
            // SupabaseAuth-backed Bearer token -> use AuthedRequest's default
            // refresh (SupabaseAuth.refreshSession), not AuthService's.
            AuthedRequest.withAuthRetry(isAuthFailure = { it.code == 401 }) {
                http.newCall(req).execute()
            }.use { resp ->
                if (!resp.isSuccessful) {
                    Log.w(TAG, "POST $path -> ${resp.code}")
                    return@withContext null
                }
                val json = JSONObject(resp.body?.string() ?: return@withContext null)
                json.optString("uuid", null)
            }
        } catch (e: Exception) {
            Log.w(TAG, "POST $path failed: ${e.message}")
            null
        }
    }
}
