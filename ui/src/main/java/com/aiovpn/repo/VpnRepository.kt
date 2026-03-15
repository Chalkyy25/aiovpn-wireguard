package com.aiovpn.repo

import android.content.Context
import com.aiovpn.api.AioApi
import com.aiovpn.api.ServerDto
import com.aiovpn.auth.TokenStore

class VpnRepository(context: Context) {

    private val tokenStore = TokenStore(context.applicationContext)
    private val api = AioApi()

    suspend fun getToken(): String? = tokenStore.getToken()

    suspend fun hasToken(): Boolean = !getToken().isNullOrBlank()

    private suspend fun requireToken(): String {
        return getToken()?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("No token (user not logged in)")
    }

    suspend fun login(username: String, password: String) {
        val response = api.login(username.trim(), password)
        tokenStore.saveToken(response.token)
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
        tokenStore.clear()
    }
}