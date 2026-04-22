package com.guildofsmiths.trademesh.data

import android.util.Log
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Looks up other users in the SmithNet profiles table.
 *
 * Row-level security on the server enforces discoverability: callers only
 * ever receive rows whose owners opted in to being found (see
 * 008_profiles_discoverability.sql). This repository is a thin client over
 * those queries.
 */
object ProfileDirectoryRepository {

    private const val TAG = "ProfileDirectory"
    private const val SEARCH_LIMIT = 20L

    /**
     * People in the same org as the current user. RLS filters out anyone
     * whose discoverability is `nobody`. Returns empty when the user has no
     * org or is offline.
     */
    suspend fun teammates(): List<ProfileRow> = withContext(Dispatchers.IO) {
        val client = SupabaseAuth.client ?: return@withContext emptyList()
        val currentUser = SupabaseAuth.currentUser.value ?: return@withContext emptyList()
        val orgId = currentUser.orgId ?: return@withContext emptyList()
        try {
            client.from("profiles")
                .select {
                    filter {
                        eq("org_id", orgId)
                        neq("id", currentUser.id)
                    }
                    limit(SEARCH_LIMIT)
                }
                .decodeList<ProfileRow>()
        } catch (e: Exception) {
            Log.w(TAG, "teammates() failed: ${e.message}")
            emptyList()
        }
    }

    /**
     * Routes to [lookupById] when the query looks like a SmithNet public ID
     * (8 alphanumeric chars, optional single dash), else [searchByName].
     * Empty/blank query returns empty.
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
        val client = SupabaseAuth.client ?: return@withContext null
        try {
            client.from("profiles")
                .select {
                    filter { eq("public_id", publicId) }
                    limit(1L)
                }
                .decodeSingleOrNull<ProfileRow>()
        } catch (e: Exception) {
            Log.w(TAG, "lookupById($publicId) failed: ${e.message}")
            null
        }
    }

    private suspend fun searchByName(prefix: String): List<ProfileRow> = withContext(Dispatchers.IO) {
        val client = SupabaseAuth.client ?: return@withContext emptyList()
        val sanitized = prefix.replace("%", "").replace("_", "").trim()
        if (sanitized.isEmpty()) return@withContext emptyList()
        try {
            client.from("profiles")
                .select {
                    filter { ilike("display_name", "$sanitized%") }
                    limit(SEARCH_LIMIT)
                }
                .decodeList<ProfileRow>()
        } catch (e: Exception) {
            Log.w(TAG, "searchByName($prefix) failed: ${e.message}")
            emptyList()
        }
    }
}
