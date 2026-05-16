package com.guildofsmiths.trademesh.service

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import java.util.concurrent.TimeUnit

/**
 * Phase 3.5 Slice 2.5: shared OkHttp client + auth interceptor.
 *
 * Earlier Slice 2 wired up [LocationService] / [PresenceApiClient] / [TimeTrackingViewModel]
 * with bare `OkHttpClient()` instances. None of them attached the access token,
 * so every authenticated POST returned 401 on a real device.
 *
 * The backend's [authenticateToken] middleware accepts either the `smithnet_access`
 * cookie OR an `Authorization: Bearer <token>` header. The Android side already
 * stores the access token in [AuthService] (SharedPreferences). Threading every
 * caller through a shared client with an interceptor that adds the Bearer header
 * is simpler than a CookieJar and avoids a second source of truth.
 */
object HttpClientFactory {

    private val authInterceptor = Interceptor { chain ->
        val original = chain.request()
        val token = AuthService.getAccessToken()
        val request = if (!token.isNullOrBlank() && original.header("Authorization") == null) {
            original.newBuilder().addHeader("Authorization", "Bearer $token").build()
        } else {
            original
        }
        chain.proceed(request)
    }

    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .addInterceptor(authInterceptor)
        .build()
}
