package com.guildofsmiths.trademesh.data

import android.util.Log
import com.guildofsmiths.trademesh.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Looks up other users via the Hetzner directory endpoints (W3 migration off
 * Supabase). Thin OkHttp client over:
 *   GET /api/profiles?q=<needle>     -> name/email people search
 *   GET /api/profiles/lookup?publicId=XXXXXXXX -> exact public-handle lookup (cross-org)
 *   GET /api/profiles/teammates      -> everyone in the caller's org (excl self)
 * All require the Hetzner Bearer token (SupabaseAuth.getAccessToken()).
 */
object ProfileDirectoryRepository {

    private const val TAG = "ProfileDirectory"

    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private fun baseUrl(): String = BuildConfig.BACKEND_URL_PRIMARY.trimEnd('/')

    /** Everyone in the caller's organization. Empty when signed out / offline. */
    suspend fun teammates(): List<ProfileRow> = withContext(Dispatchers.IO) {
        val json = get("/api/profiles/teammates") ?: return@withContext emptyList()
        parseProfiles(json.optJSONArray("profiles"))
    }

    /**
     * Routes to a public-ID lookup when the query looks like a SmithNet public ID
     * (8 alphanumeric chars, optional single dash), else a name/email search.
     */
    suspend fun search(query: String): List<ProfileRow> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return emptyList()
        val idCandidate = trimmed.replace("-", "").uppercase()
        return if (idCandidate.length == 8 && idCandidate.all { it.isLetterOrDigit() }) {
            listOfNotNull(lookupById(idCandidate))
        } else {
            searchByName(trimmed)
        }
    }

    private suspend fun lookupById(publicId: String): ProfileRow? = withContext(Dispatchers.IO) {
        val json = get("/api/profiles/lookup?publicId=$publicId") ?: return@withContext null
        val obj = json.optJSONObject("profile") ?: return@withContext null
        parseProfile(obj)
    }

    private suspend fun searchByName(prefix: String): List<ProfileRow> = withContext(Dispatchers.IO) {
        val sanitized = prefix.replace("%", "").replace("_", "").trim()
        if (sanitized.length < 2) return@withContext emptyList()  // backend requires q >= 2
        val encoded = java.net.URLEncoder.encode(sanitized, "UTF-8")
        val json = get("/api/profiles?q=$encoded") ?: return@withContext emptyList()
        parseProfiles(json.optJSONArray("profiles"))
    }

    // ── http + mapping ──────────────────────────────────────────────────────

    /** GET an authenticated endpoint; returns the parsed JSON body or null. */
    private fun get(path: String): JSONObject? {
        val token = SupabaseAuth.getAccessToken() ?: return null
        return try {
            val req = Request.Builder()
                .url("${baseUrl()}$path")
                .header("Authorization", "Bearer $token")
                .get()
                .build()
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    Log.w(TAG, "GET $path -> ${resp.code}")
                    return null
                }
                val body = resp.body?.string() ?: return null
                JSONObject(body)
            }
        } catch (e: Exception) {
            Log.w(TAG, "GET $path failed: ${e.message}")
            null
        }
    }

    private fun parseProfiles(arr: JSONArray?): List<ProfileRow> {
        if (arr == null) return emptyList()
        return (0 until arr.length()).mapNotNull { i -> arr.optJSONObject(i)?.let { parseProfile(it) } }
    }

    /** Map a backend profile object onto the app's ProfileRow. Fields the backend
     *  directory does not carry (trade, hourly_rate, discoverability) stay at
     *  their ProfileRow defaults. */
    private fun parseProfile(o: JSONObject): ProfileRow = ProfileRow(
        id = o.optString("id"),
        email = o.optString("email"),
        display_name = o.optString("displayName", o.optString("email").substringBefore("@")),
        role = o.optString("role", "solo"),
        public_id = o.optString("publicId", null),
        avatar_url = o.optString("avatarUrl", null),
        org_id = o.optString("organizationId", null),
    )
}
