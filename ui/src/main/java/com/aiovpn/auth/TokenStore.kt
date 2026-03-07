package com.aiovpn.auth

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.dataStore by preferencesDataStore(name = "aio_auth")

class TokenStore(private val context: Context) {
    private val KEY_TOKEN = stringPreferencesKey("token")

    suspend fun saveToken(token: String) {
        context.dataStore.edit { it[KEY_TOKEN] = token }
    }

    suspend fun getToken(): String? {
        val prefs = context.dataStore.data.first()
        return prefs[KEY_TOKEN]
    }

    suspend fun clear() {
        context.dataStore.edit { it.remove(KEY_TOKEN) }
    }
}