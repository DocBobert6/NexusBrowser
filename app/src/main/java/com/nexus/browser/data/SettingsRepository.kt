package com.nexus.browser.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "nexus_settings")

/**
 * Persisted user settings. Single source of truth for theme, ad blocking,
 * and cursor — replaces the local throwaway state the alpha shipped with.
 */
class SettingsRepository(private val context: Context) {

    companion object {
        private val DARK_THEME = booleanPreferencesKey("dark_theme")
        private val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        private val ADBLOCK_ENABLED = booleanPreferencesKey("adblock_enabled")
        private val CURSOR_ENABLED = booleanPreferencesKey("cursor_enabled")
    }

    val darkTheme: Flow<Boolean> =
        context.settingsDataStore.data.map { it[DARK_THEME] ?: false }

    val dynamicColor: Flow<Boolean> =
        context.settingsDataStore.data.map { it[DYNAMIC_COLOR] ?: false }

    val adBlockEnabled: Flow<Boolean> =
        context.settingsDataStore.data.map { it[ADBLOCK_ENABLED] ?: true }

    val cursorEnabled: Flow<Boolean> =
        context.settingsDataStore.data.map { it[CURSOR_ENABLED] ?: true }

    suspend fun setDarkTheme(value: Boolean) {
        context.settingsDataStore.edit { it[DARK_THEME] = value }
    }

    suspend fun setDynamicColor(value: Boolean) {
        context.settingsDataStore.edit { it[DYNAMIC_COLOR] = value }
    }

    suspend fun setAdBlockEnabled(value: Boolean) {
        context.settingsDataStore.edit { it[ADBLOCK_ENABLED] = value }
    }

    suspend fun setCursorEnabled(value: Boolean) {
        context.settingsDataStore.edit { it[CURSOR_ENABLED] = value }
    }
}
