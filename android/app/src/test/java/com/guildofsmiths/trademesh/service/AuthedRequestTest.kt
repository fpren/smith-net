package com.guildofsmiths.trademesh.service

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Task 3 (401 -> refreshSession wiring): withAuthRetry is the shared REST
 * seam that decides whether a suspend network call gets one refresh + one
 * retry. Covered with a fake block + fake refresher, injected via the
 * [AuthedRequest.withAuthRetry] `refresh` parameter so no real
 * SupabaseAuth/AuthService network call happens in a pure JVM test.
 */
class AuthedRequestTest {

    @Test
    fun `no auth failure passes through with zero refreshes`() = runTest {
        var blockCalls = 0
        var refreshCalls = 0

        val result = AuthedRequest.withAuthRetry(
            isAuthFailure = { it == "auth_fail" },
            refresh = { refreshCalls++; true },
        ) {
            blockCalls++
            "ok"
        }

        assertEquals("ok", result)
        assertEquals(1, blockCalls)
        assertEquals(0, refreshCalls)
    }

    @Test
    fun `auth failure triggers one refresh and one retry that then succeeds`() = runTest {
        var blockCalls = 0
        var refreshCalls = 0

        val result = AuthedRequest.withAuthRetry(
            isAuthFailure = { it == "auth_fail" },
            refresh = { refreshCalls++; true },
        ) {
            blockCalls++
            if (blockCalls == 1) "auth_fail" else "ok"
        }

        assertEquals("ok", result)
        assertEquals(2, blockCalls)
        assertEquals(1, refreshCalls)
    }

    @Test
    fun `still failing after refresh returns the second result with exactly one refresh`() = runTest {
        var blockCalls = 0
        var refreshCalls = 0

        val result = AuthedRequest.withAuthRetry(
            isAuthFailure = { it == "auth_fail" },
            refresh = { refreshCalls++; true },
        ) {
            blockCalls++
            "auth_fail"
        }

        assertEquals("auth_fail", result)
        assertEquals(2, blockCalls)
        assertEquals(1, refreshCalls)
    }

    @Test
    fun `refresh failure is still followed by exactly one retry`() = runTest {
        var blockCalls = 0
        var refreshCalls = 0

        val result = AuthedRequest.withAuthRetry(
            isAuthFailure = { it == "auth_fail" },
            refresh = { refreshCalls++; false },
        ) {
            blockCalls++
            if (blockCalls == 1) "auth_fail" else "ok"
        }

        assertEquals("ok", result)
        assertEquals(2, blockCalls)
        assertEquals(1, refreshCalls)
    }

    @Test
    fun `retry re-reads the current token after refresh instead of reusing a stale one`() = runTest {
        // Regression for the stale-Request bug (PublicLinkClient /
        // JobReportClient built an immutable okhttp3.Request -- baking in the
        // Authorization header -- BEFORE calling withAuthRetry, so the retry
        // resent the OLD token and 401'd again. A correctly-wired caller
        // reads the current token INSIDE the block on every invocation. Model
        // that here with a mutable "currentToken" var the block reads each
        // call; refresh() flips it to the new value; assert the second
        // attempt observed "new", not a captured-once "old".
        var currentToken = "old"
        val tokensSeenByBlock = mutableListOf<String>()
        var refreshCalls = 0

        val result = AuthedRequest.withAuthRetry(
            isAuthFailure = { it == 401 },
            refresh = {
                refreshCalls++
                currentToken = "new"
                true
            },
        ) {
            tokensSeenByBlock.add(currentToken)
            if (currentToken == "old") 401 else 200
        }

        assertEquals(200, result)
        assertEquals(1, refreshCalls)
        assertEquals(listOf("old", "new"), tokensSeenByBlock)
    }

    @Test
    fun `omitting refresh never touches the default (SupabaseAuth) on the no-failure path`() = runTest {
        // Confirms callers can omit `refresh` entirely (relying on the
        // AuthedRequest default) without that default ever being invoked
        // when isAuthFailure never trips -- so this stays a pure JVM test
        // with no live SupabaseAuth/Android dependency required.
        var blockCalls = 0
        val result = AuthedRequest.withAuthRetry<String>(
            isAuthFailure = { false },
        ) {
            blockCalls++
            "untouched"
        }
        assertEquals("untouched", result)
        assertEquals(1, blockCalls)
    }
}
