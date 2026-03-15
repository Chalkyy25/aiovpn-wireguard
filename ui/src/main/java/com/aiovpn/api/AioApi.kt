package com.aiovpn.api

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
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(40, TimeUnit.SECONDS)
        .callTimeout(45, TimeUnit.SECONDS)
        .build()

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val loginAdapter by lazy { moshi.adapter(LoginResponse::class.java) }
    private val serversAdapter by lazy { moshi.adapter(ServersResponse::class.java) }

    @Throws(IOException::class)
    fun login(username: String, password: String): LoginResponse {
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
            return loginAdapter.fromJson(raw) ?: throw IOException("Bad JSON: $raw")
        }
    }

    @Throws(IOException::class)
    fun servers(token: String): List<ServerDto> {
        val req = Request.Builder()
            .url("$baseUrl/api/wg/servers")
            .header("Accept", "application/json")
            .header("Authorization", "Bearer $token")
            .get()
            .build()

        client.newCall(req).execute().use { res ->
            val raw = res.body?.string().orEmpty()
            if (!res.isSuccessful) throw IOException("Servers failed ${res.code}: $raw")

            val parsed = try {
                serversAdapter.fromJson(raw)
            } catch (e: Exception) {
                throw IOException("Parsing failed: ${e.message}", e)
            } ?: throw IOException("Empty response body")

            // Return all servers returned by the API without filtering
            return parsed.data
        }
    }

    @Throws(IOException::class)
    fun wgConfig(token: String, serverId: Int): String {
        val req = Request.Builder()
            .url("$baseUrl/api/wg/config?server_id=$serverId")
            .header("Accept", "application/json")
            .header("Authorization", "Bearer $token")
            .get()
            .build()

        client.newCall(req).execute().use { res ->
            val raw = res.body?.string().orEmpty()
            if (!res.isSuccessful) throw IOException("Config failed ${res.code}: $raw")
            return raw
        }
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
    val username: String
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
