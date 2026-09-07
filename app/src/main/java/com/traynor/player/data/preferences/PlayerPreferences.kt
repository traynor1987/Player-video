package com.traynor.player.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("player_preferences")

class PlayerPreferences(private val context: Context) {
    private object Keys {
        val setupComplete = booleanPreferencesKey("setup_complete")
        val activeSourceId = longPreferencesKey("active_source_id")
        val lastChannelId = longPreferencesKey("last_channel_id")
        val previousChannelId = longPreferencesKey("previous_channel_id")
        val playLastOnLaunch = booleanPreferencesKey("play_last_on_launch")
        val theme = stringPreferencesKey("theme")
    }
    val setupComplete = context.dataStore.data.map { it[Keys.setupComplete] ?: false }
    val activeSourceId = context.dataStore.data.map { it[Keys.activeSourceId] }
    val playLastOnLaunch = context.dataStore.data.map { it[Keys.playLastOnLaunch] ?: false }
    suspend fun finishSetup(sourceId: Long) = context.dataStore.edit { it[Keys.setupComplete] = true; it[Keys.activeSourceId] = sourceId }
    suspend fun selectSource(id: Long) = context.dataStore.edit { it[Keys.activeSourceId] = id }
    suspend fun rememberChannel(id: Long) = context.dataStore.edit {
        val current = it[Keys.lastChannelId]
        if (current != null && current != id) it[Keys.previousChannelId] = current
        it[Keys.lastChannelId] = id
    }
    suspend fun setPlayLast(enabled: Boolean) = context.dataStore.edit { it[Keys.playLastOnLaunch] = enabled }
    suspend fun setTheme(value: String) = context.dataStore.edit { it[Keys.theme] = value }
}
