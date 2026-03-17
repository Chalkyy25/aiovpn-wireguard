/*
 * Copyright © 2017-2026 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aiovpn.util

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "aio_settings")

class SettingsStore(private val context: Context) {
    private val KEY_KILL_SWITCH = booleanPreferencesKey("kill_switch")
    private val KEY_AUTO_CONNECT = booleanPreferencesKey("auto_connect")
    private val KEY_LAST_SERVER_ID = intPreferencesKey("last_server_id")
    private val KEY_LAST_SERVER_LABEL = stringPreferencesKey("last_server_label")
    private val KEY_EXCLUDED_APPS = stringSetPreferencesKey("excluded_apps")

    val killSwitchFlow: Flow<Boolean> = context.settingsDataStore.data.map { it[KEY_KILL_SWITCH] ?: false }
    val autoConnectFlow: Flow<Boolean> = context.settingsDataStore.data.map { it[KEY_AUTO_CONNECT] ?: false }
    
    val lastServerIdFlow: Flow<Int?> = context.settingsDataStore.data.map { it[KEY_LAST_SERVER_ID] }
    val lastServerLabelFlow: Flow<String?> = context.settingsDataStore.data.map { it[KEY_LAST_SERVER_LABEL] }

    val excludedAppsFlow: Flow<Set<String>> = context.settingsDataStore.data.map { it[KEY_EXCLUDED_APPS] ?: emptySet() }

    suspend fun setKillSwitch(enabled: Boolean) {
        context.settingsDataStore.edit { it[KEY_KILL_SWITCH] = enabled }
    }

    suspend fun setAutoConnect(enabled: Boolean) {
        context.settingsDataStore.edit { it[KEY_AUTO_CONNECT] = enabled }
    }

    suspend fun saveLastServer(id: Int, label: String) {
        context.settingsDataStore.edit {
            it[KEY_LAST_SERVER_ID] = id
            it[KEY_LAST_SERVER_LABEL] = label
        }
    }

    suspend fun setExcludedApps(packageNames: Set<String>) {
        context.settingsDataStore.edit { it[KEY_EXCLUDED_APPS] = packageNames }
    }
}
