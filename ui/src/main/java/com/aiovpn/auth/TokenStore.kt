package com.aiovpn.auth

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.dataStore by preferencesDataStore(name = "aio_auth")

class TokenStore(private val context: Context) {
    private val KEY_TOKEN = stringPreferencesKey("token")
    private val KEY_USERNAME = stringPreferencesKey("username")
    private val KEY_EXPIRY = stringPreferencesKey("expiry")
    private val KEY_DEVICES_ALLOWED = stringPreferencesKey("devices_allowed")

    suspend fun saveAuth(token: String, username: String, expiry: String?, devicesAllowed: String?) {
        context.dataStore.edit {
            it[KEY_TOKEN] = token
            it[KEY_USERNAME] = username
            if (expiry != null) it[KEY_EXPIRY] = expiry else it.remove(KEY_EXPIRY)
            if (devicesAllowed != null) it[KEY_DEVICES_ALLOWED] = devicesAllowed else it.remove(KEY_DEVICES_ALLOWED)
        }
    }

    suspend fun getToken(): String? = context.dataStore.data.first()[KEY_TOKEN]

    suspend fun getUsername(): String? = context.dataStore.data.first()[KEY_USERNAME]

    suspend fun getExpiry(): String? = context.dataStore.data.first()[KEY_EXPIRY]

    suspend fun getDevicesAllowed(): String? = context.dataStore.data.first()[KEY_DEVICES_ALLOWED]

    suspend fun clear() {
        context.dataStore.edit {
            it.remove(KEY_TOKEN)
            it.remove(KEY_USERNAME)
            it.remove(KEY_EXPIRY)
            it.remove(KEY_DEVICES_ALLOWED)
        }
    }
}
