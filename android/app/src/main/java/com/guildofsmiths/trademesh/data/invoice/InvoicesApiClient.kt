// android/app/src/main/java/com/guildofsmiths/trademesh/data/invoice/InvoicesApiClient.kt
package com.guildofsmiths.trademesh.data.invoice

import android.util.Log
import com.guildofsmiths.trademesh.BuildConfig
import com.guildofsmiths.trademesh.ui.invoice.InvoiceLineItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/**
 * Wire-layer interface so InvoicesPushWorker / InvoicesOutbox can be tested
 * against a fake. The real implementation talks to /api/invoices via OkHttp,
 * following the PresenceApiClient pattern.
 */
interface InvoicesApi {
    /**
     * Posts a pre-serialized invoice body (built by InvoiceJsonMapper.createBody
     * at enqueue time and stored in the outbox row). Returns the server's
     * backend invoice id. Throws ApiClientError on 4xx; IOException on 5xx / transient.
     *
     * This variant exists because the worker reads payloadJson back from the
     * outbox; passing the structured Invoice object would force a re-build
     * with empty fields (everything except id + lineItems is lost when the
     * row is read back).
     */
    suspend fun createInvoiceWithPayload(payloadJson: String): String

    /** Adds a single line item to a backend invoice. */
    suspend fun addLineItem(backendInvoiceId: String, item: InvoiceLineItem)

    /** PATCH /api/invoices/{id}/status. */
    suspend fun setStatus(backendInvoiceId: String, status: String)

    /** DELETE /api/invoices/{id}. 404 is treated as success (idempotent). */
    suspend fun deleteInvoice(backendInvoiceId: String)
}

/** 4xx response — caller should mark the outbox row failed, not retry. */
class ApiClientError(val httpStatus: Int, message: String) : RuntimeException(message)

class InvoicesApiClient(private val client: OkHttpClient) : InvoicesApi {

    companion object {
        private const val TAG = "InvoicesApiClient"
        private val JSON = "application/json; charset=utf-8".toMediaType()
    }

    private val baseUrl: String get() = BuildConfig.BACKEND_URL

    override suspend fun createInvoiceWithPayload(payloadJson: String): String = withContext(Dispatchers.IO) {
        val body = payloadJson.toRequestBody(JSON)
        val req = Request.Builder().url("$baseUrl/api/invoices").post(body).build()
        client.newCall(req).execute().use { res ->
            if (res.code in 400..499) {
                throw ApiClientError(res.code, "createInvoice HTTP ${res.code}: ${res.body?.string()}")
            }
            if (!res.isSuccessful) throw java.io.IOException("createInvoice HTTP ${res.code}")
            val json = JSONObject(res.body?.string() ?: "{}")
            json.getJSONObject("invoice").getString("id")
        }
    }

    override suspend fun addLineItem(backendInvoiceId: String, item: InvoiceLineItem) = withContext(Dispatchers.IO) {
        val body = InvoiceJsonMapper.lineItemBody(item).toRequestBody(JSON)
        val req = Request.Builder()
            .url("$baseUrl/api/invoices/$backendInvoiceId/line-items")
            .post(body)
            .build()
        client.newCall(req).execute().use { res ->
            if (res.code in 400..499) {
                throw ApiClientError(res.code, "addLineItem HTTP ${res.code}")
            }
            if (!res.isSuccessful) throw java.io.IOException("addLineItem HTTP ${res.code}")
        }
    }

    override suspend fun setStatus(backendInvoiceId: String, status: String) = withContext(Dispatchers.IO) {
        val body = InvoiceJsonMapper.statusBody(status).toRequestBody(JSON)
        val req = Request.Builder()
            .url("$baseUrl/api/invoices/$backendInvoiceId/status")
            .patch(body)
            .build()
        client.newCall(req).execute().use { res ->
            if (res.code in 400..499) {
                throw ApiClientError(res.code, "setStatus HTTP ${res.code}")
            }
            if (!res.isSuccessful) throw java.io.IOException("setStatus HTTP ${res.code}")
        }
    }

    override suspend fun deleteInvoice(backendInvoiceId: String) = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url("$baseUrl/api/invoices/$backendInvoiceId")
            .delete()
            .build()
        client.newCall(req).execute().use { res ->
            if (res.code == 404) {
                Log.d(TAG, "deleteInvoice 404 — already gone, treating as success")
                return@withContext
            }
            if (res.code in 400..499) {
                throw ApiClientError(res.code, "deleteInvoice HTTP ${res.code}")
            }
            if (!res.isSuccessful) throw java.io.IOException("deleteInvoice HTTP ${res.code}")
        }
    }
}
