package com.aiovpn.repo

import android.content.Context
import com.aiovpn.api.AioApi
import com.aiovpn.api.ServerDto
import com.aiovpn.auth.TokenStore

class VpnRepository(context: Context) {

    private val tokenStore = TokenStore(context.applicationContext)
    private val api = AioApi()

    suspend fun getToken(): String? = tokenStore.getToken()
    suspend fun getUsername(): String? = tokenStore.getUsername()
    suspend fun getExpiry(): String? = tokenStore.getExpiry()
    suspend fun getDevicesAllowed(): Int? = tokenStore.getDevicesAllowed()

    fun getTokenSync(): String? {
        return tokenStore.getTokenSync()
    }

    suspend fun hasToken(): Boolean {
        return !getToken().isNullOrBlank()
    }

    private suspend fun requireToken(): String {
        return getToken()?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("No token (user not logged in)")
    }

    suspend fun login(username: String, password: String) {
        val response = api.login(username.trim(), password)

        tokenStore.saveAuth(
            token = response.token,
            username = response.user?.username ?: username.trim(),
            expiry = response.user?.expires,
            devicesAllowed = response.user?.max_conn
        )
    }

    suspend fun refreshProfile() {
        val token = requireToken()
        val profile = api.profile(token)

        tokenStore.saveAuth(
            token = token,
            username = profile.username,
            expiry = profile.expires,
            devicesAllowed = profile.max_conn
        )
    }

    suspend fun servers(): List<ServerDto> {
        val token = requireToken()
        return api.servers(token)
    }

    suspend fun wgConfig(serverId: Int): String {
        val token = requireToken()
        return api.wgConfig(token, serverId)
    }

    suspend fun logout() {
        val token = getToken()
        if (!token.isNullOrBlank()) {
            try {
                api.logout(token)
            } catch (_: Exception) {
            }
        }
        tokenStore.clear()
    }
}