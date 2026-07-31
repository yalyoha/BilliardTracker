package com.example.billiardtracker.data.remote

import com.example.billiardtracker.data.prefs.UserPrefs
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response

class AuthInterceptor(private val prefs: UserPrefs) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val token = runBlocking { prefs.getToken() }
        val req = if (token.isNullOrEmpty() || request.header("Authorization") != null) {
            request
        } else {
            request.newBuilder().header("Authorization", "Bearer $token").build()
        }
        return chain.proceed(req)
    }
}
