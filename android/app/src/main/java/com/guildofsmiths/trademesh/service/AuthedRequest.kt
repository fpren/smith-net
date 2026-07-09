package com.guildofsmiths.trademesh.service

import android.util.Log
import com.guildofsmiths.trademesh.data.SupabaseAuth
import java.io.Closeable

/**
 * Shared 401-retry wrapper for suspend REST calls.
 *
 * The app currently keeps two independent auth-token stores: [AuthService]
 * (read by [HttpClientFactory]'s interceptor and by [ChatManager]'s WS Bearer
 * header) and [SupabaseAuth] (read directly by a few standalone clients, e.g.
 * PublicLinkClient / JobReportClient). Refreshing the wrong one is a silent
 * no-op, so callers whose Bearer token flows through [HttpClientFactory] must
 * pass `refresh = { AuthService.refreshToken() }` explicitly; the default here
 * only covers the [SupabaseAuth]-backed call sites.
 */
object AuthedRequest {

    private const val TAG = "AuthedRequest"

    /**
     * Runs [block]. If the result looks like an auth rejection per
     * [isAuthFailure], calls [refresh] once and runs [block] a second time,
     * returning whatever that second attempt yields -- success or failure,
     * with no further retry. If [refresh] reports failure, logs
     * `[x] session refresh failed` but still performs the single retry (the
     * server gets the final say on whether the still-stale token works).
     *
     * [refresh] defaults to [SupabaseAuth.refreshSession]. It's declared
     * nullable (default `null`, resolved to the real default inside the
     * function body) rather than `= SupabaseAuth::refreshSession` directly on
     * the parameter -- Kotlin 1.8's IR backend does not support a suspend
     * function call in a default-parameter-value position ("Unsupported
     * [suspend function calls in a context of default parameter value]"),
     * so this is the least-invasive way to get the same effective default
     * while keeping `refresh` injectable for tests.
     */
    suspend fun <T> withAuthRetry(
        isAuthFailure: (T) -> Boolean,
        refresh: (suspend () -> Boolean)? = null,
        block: suspend () -> T,
    ): T {
        val first = block()
        if (!isAuthFailure(first)) return first

        // Don't leak the failed response's connection/body while we refresh.
        (first as? Closeable)?.close()

        val doRefresh = refresh ?: SupabaseAuth::refreshSession
        val refreshed = doRefresh()
        if (!refreshed) {
            Log.w(TAG, "[x] session refresh failed")
        }
        return block()
    }
}
