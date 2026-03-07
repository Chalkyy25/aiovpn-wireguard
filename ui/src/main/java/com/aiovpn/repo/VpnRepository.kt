package com.aiovpn.repo

import android.content.Context
import com.aiovpn.api.AioApi
import com.aiovpn.api.ServerDto
import com.aiovpn.auth.TokenStore

class VpnRepository(context: Context) {

    private val appContext = context.applicationContext
    private val api = AioApi()
    private val tokens = TokenStore(appContext)

    suspend fun getToken(): String? = tokens.getToken()

    suspend fun hasToken(): Boolean = !getToken().isNullOrBlank()

    private suspend fun requireToken(): String {
        return getToken() ?: throw IllegalStateException("No token (user not logged in)")
    }

    suspend fun login(username: String, password: String) {
        val res = api.login(username.trim(), password)
        tokens.saveToken(res.token)
    }

    suspend fun servers(): List<ServerDto> {
        return api.servers(requireToken())
    }

    suspend fun wgConfig(serverId: Int): String {
        return api.wgConfig(requireToken(), serverId)
    }

    suspend fun logout() {
        // Later: call POST /api/auth/logout as well (revoke token server-side).
        tokens.clear()
    }
}