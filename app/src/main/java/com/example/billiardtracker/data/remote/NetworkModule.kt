package com.example.billiardtracker.data.remote

import com.example.billiardtracker.data.prefs.UserPrefs
import com.example.billiardtracker.data.telemetry.DevLogInterceptor
import com.example.billiardtracker.data.telemetry.DevLogger
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit

object NetworkModule {
    val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    fun provideRetrofit(
        baseUrl: String,
        userPrefs: UserPrefs,
        devLoggerProvider: () -> DevLogger? = { null },
    ): Retrofit {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        val client = OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(userPrefs))
            .addInterceptor(DevLogInterceptor(devLoggerProvider))
            .addInterceptor(logging)
            .build()
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
    }
}
