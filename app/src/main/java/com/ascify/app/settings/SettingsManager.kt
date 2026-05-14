package com.ascify.app.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "ascify_settings")

@Singleton
class SettingsManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private object Keys {
        val CHARACTER_SET = stringPreferencesKey("character_set")
        val COLOR_PALETTE = stringPreferencesKey("color_palette")
        val ASCII_DENSITY = stringPreferencesKey("ascii_density")
        val EXPORT_FORMAT = stringPreferencesKey("export_format")
        val SAVE_ORIGINAL = booleanPreferencesKey("save_original")
        val EDGE_ENHANCEMENT = booleanPreferencesKey("edge_enhancement")
        val NIGHT_MODE = booleanPreferencesKey("night_mode")
        val ADAPTIVE_RENDERING = booleanPreferencesKey("adaptive_rendering")
        val SHOW_FPS = booleanPreferencesKey("show_fps")
        val FONT_SIZE = floatPreferencesKey("font_size")
    }

    val settingsFlow: Flow<AppSettings> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences())
            else throw exception
        }
        .map { prefs ->
            AppSettings(
                characterSet = prefs[Keys.CHARACTER_SET]?.let {
                    runCatching { CharacterSet.valueOf(it) }.getOrDefault(CharacterSet.CLASSIC)
                } ?: CharacterSet.CLASSIC,
                colorPalette = prefs[Keys.COLOR_PALETTE]?.let {
                    runCatching { ColorPalette.valueOf(it) }.getOrDefault(ColorPalette.FULL_RGB)
                } ?: ColorPalette.FULL_RGB,
                asciiDensity = prefs[Keys.ASCII_DENSITY]?.let {
                    runCatching { AsciiDensity.valueOf(it) }.getOrDefault(AsciiDensity.MEDIUM)
                } ?: AsciiDensity.MEDIUM,
                exportFormat = prefs[Keys.EXPORT_FORMAT]?.let {
                    runCatching { ExportFormat.valueOf(it) }.getOrDefault(ExportFormat.PNG)
                } ?: ExportFormat.PNG,
                saveOriginalFrame = prefs[Keys.SAVE_ORIGINAL] ?: false,
                edgeEnhancement = prefs[Keys.EDGE_ENHANCEMENT] ?: false,
                nightModeEnabled = prefs[Keys.NIGHT_MODE] ?: false,
                adaptiveRendering = prefs[Keys.ADAPTIVE_RENDERING] ?: true,
                showFpsCounter = prefs[Keys.SHOW_FPS] ?: false,
                fontSize = prefs[Keys.FONT_SIZE] ?: 6f
            )
        }

    suspend fun updateSettings(settings: AppSettings) {
        context.dataStore.edit { prefs ->
            prefs[Keys.CHARACTER_SET] = settings.characterSet.name
            prefs[Keys.COLOR_PALETTE] = settings.colorPalette.name
            prefs[Keys.ASCII_DENSITY] = settings.asciiDensity.name
            prefs[Keys.EXPORT_FORMAT] = settings.exportFormat.name
            prefs[Keys.SAVE_ORIGINAL] = settings.saveOriginalFrame
            prefs[Keys.EDGE_ENHANCEMENT] = settings.edgeEnhancement
            prefs[Keys.NIGHT_MODE] = settings.nightModeEnabled
            prefs[Keys.ADAPTIVE_RENDERING] = settings.adaptiveRendering
            prefs[Keys.SHOW_FPS] = settings.showFpsCounter
            prefs[Keys.FONT_SIZE] = settings.fontSize
        }
    }
}
