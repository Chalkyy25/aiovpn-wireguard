/*
 * Copyright © 2017-2026 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aiovpn.api

import android.net.TrafficStats
import android.util.Log
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

class AioApi(
    private val baseUrl: String = "https://panel.aiovpn.co.uk"
) {
    // USE LAZY TO PREVENT ANR DURING CLASS INITIALIZATION
    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(40, TimeUnit.SECONDS)
            .callTimeout(45, TimeUnit.SECONDS)
            .build()
    }

    private val moshi by lazy {
        Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()
    }

    private val loginAdapter by lazy { moshi.adapter(LoginResponse::class.java) }
    private val profileAdapter by lazy { moshi.adapter(ProfileResponse::class.java) }
    private val serversAdapter by lazy { moshi.adapter(ServersResponse::class.java) }

    @Throws(IOException::class)
    fun login(username: String, password: String): LoginResponse = withSocketTag(NETWORK_TAG_AUTH) {
        val body = FormBody.Builder()
            .add("username", username.trim())
            .add("password", password)
            .build()

        val req = Request.Builder()
            .url("$baseUrl/api/auth/login")
            .header("Accept", "application/json")
            .post(body)
            .build()

        client.newCall(req).execute().use { res ->
            val raw = res.body?.string().orEmpty()
            if (!res.isSuccessful) throw IOException("Login failed ${res.code}: $raw")
            loginAdapter.fromJson(raw) ?: throw IOException("Bad JSON: $raw")
        }
    }

    @Throws(IOException::class)
    fun profile(token: String): ProfileResponse = withSocketTag(NETWORK_TAG_PROFILE) {
        val req = Request.Builder()
            .url("$baseUrl/api/auth/me")
            .header("Accept", "application/json")
            .header("Authorization", "Bearer $token")
            .get()
            .build()

        client.newCall(req).execute().use { res ->
            val raw = res.body?.string().orEmpty()
            if (!res.isSuccessful) throw IOException("Profile failed ${res.code}: $raw")
            profileAdapter.fromJson(raw) ?: throw IOException("Bad JSON: $raw")
        }
    }

    @Throws(IOException::class)
    fun servers(token: String): List<ServerDto> = withSocketTag(NETWORK_TAG_SERVERS) {
        val req = Request.Builder()
            .url("$baseUrl/api/wg/servers")
            .header("Accept", "application/json")
            .header("Authorization", "Bearer $token")
            .get()
            .build()

        client.newCall(req).execute().use { res ->
            val raw = res.body?.string().orEmpty()
            if (!res.isSuccessful) {
                Log.e(TAG, "Servers failed code=${res.code} body=$raw")
                throw IOException("Servers failed ${res.code}: $raw")
            }

            val parsed = try {
                serversAdapter.fromJson(raw)
            } catch (e: Exception) {
                Log.e(TAG, "Parsing failed for body=$raw", e)
                throw IOException("Parsing failed: ${e.message}", e)
            } ?: throw IOException("Empty response body")

            parsed.data
        }
    }

    @Throws(IOException::class)
    fun wgConfig(token: String, serverId: Int): String = withSocketTag(NETWORK_TAG_CONFIG) {
        val req = Request.Builder()
            .url("$baseUrl/api/wg/config?server_id=$serverId")
            .header("Accept", "application/json")
            .header("Authorization", "Bearer $token")
            .get()
            .build()

        client.newCall(req).execute().use { res ->
            val raw = res.body?.string().orEmpty()
            if (!res.isSuccessful) throw IOException("Config failed ${res.code}: $raw")
            raw
        }
    }

    private inline fun <T> withSocketTag(tag: Int, block: () -> T): T {
        TrafficStats.setThreadStatsTag(tag)
        return try {
            block()
        } finally {
            TrafficStats.clearThreadStatsTag()
        }
    }

    private companion object {
        private const val TAG = "AioApi"
        private const val NETWORK_TAG_AUTH = 0xA101
        private const val NETWORK_TAG_SERVERS = 0xA102
        private const val NETWORK_TAG_CONFIG = 0xA103
        private const val NETWORK_TAG_PROFILE = 0xA104
    }
}

@JsonClass(generateAdapter = true)
data class LoginResponse(
    val token: String,
    val user: UserDto? = null
)

@JsonClass(generateAdapter = true)
data class UserDto(
    val id: Int,
    val username: String,
    val active: Boolean? = null,
    val expires: String? = null,
    val max_conn: Int? = null
)

@JsonClass(generateAdapter = true)
data class ProfileResponse(
    val id: Int,
    val username: String,
    val expires: String? = null,
    val max_conn: Int? = null
)

@JsonClass(generateAdapter = true)
data class ServersResponse(
    val data: List<ServerDto>
)

@JsonClass(generateAdapter = true)
data class ServerDto(
    val id: Int,
    val name: String? = null,
    val label: String? = null,
    val endpoint: String? = null,
    val port: Int? = null,
    val country: String? = null,
    val city: String? = null
)
