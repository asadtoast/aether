package com.asadtoast.aether.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsManager(private val context: Context) {

    companion object {
        val API_KEY = stringPreferencesKey("api_key")
        val SERVER_IP = stringPreferencesKey("server_ip")
        val PORT_KEY = stringPreferencesKey("server_port")
        val DARK_MODE_KEY = booleanPreferencesKey("dark_mode")
        val GRID_COLUMNS_KEY = intPreferencesKey("grid_columns")
        val HIDE_PDF_KEY = booleanPreferencesKey("hide_pdf")
        val HIDE_PSD_KEY = booleanPreferencesKey("hide_psd")
        val HIDE_ZIP_KEY = booleanPreferencesKey("hide_zip")
        val HIDE_VIDEOS_KEY = booleanPreferencesKey("hide_videos")
        val GIF_AUTO_LOOP_KEY = booleanPreferencesKey("gif_auto_loop")
        val VISIBLE_CATEGORIES_KEY = stringSetPreferencesKey("visible_categories")
        val PRIVACY_KEYBOARD_KEY = booleanPreferencesKey("privacy_keyboard")
        val MAX_HAMMING_DISTANCE_KEY = intPreferencesKey("max_hamming_distance")
        val FILE_DOMAIN_KEY = stringPreferencesKey("file_domain")
        val TAG_DOMAIN_KEY = stringPreferencesKey("tag_domain")
        val BACKUP_DESTINATION_DOMAIN_KEY = stringPreferencesKey("backup_destination_domain")
        val DEFAULT_CATEGORIES = setOf("creator", "artist", "series", "copyright", "character", "general", "system", "meta")
    }

    suspend fun saveSettings(apiKey: String, serverIp: String, port: String) {
        context.dataStore.edit { settings ->
            settings[API_KEY] = apiKey
            settings[SERVER_IP] = serverIp
            settings[PORT_KEY] = port
        }
    }

    suspend fun saveDarkMode(enabled: Boolean) {
        context.dataStore.edit { preferences -> preferences[DARK_MODE_KEY] = enabled }
    }

    suspend fun saveGridColumns(columns: Int) {
        context.dataStore.edit { preferences -> preferences[GRID_COLUMNS_KEY] = columns }
    }

    suspend fun saveHidePdf(enabled: Boolean) {
        context.dataStore.edit { it[HIDE_PDF_KEY] = enabled }
    }
    suspend fun saveHidePsd(enabled: Boolean) {
        context.dataStore.edit { it[HIDE_PSD_KEY] = enabled }
    }
    suspend fun saveHideZip(enabled: Boolean) {
        context.dataStore.edit { it[HIDE_ZIP_KEY] = enabled }
    }
    suspend fun saveGifAutoLoop(enabled: Boolean) {
        context.dataStore.edit { it[GIF_AUTO_LOOP_KEY] = enabled }
    }
    suspend fun saveVisibleCategories(categories: Set<String>) {
        context.dataStore.edit { it[VISIBLE_CATEGORIES_KEY] = categories }
    }
    suspend fun savePrivacyKeyboard(enabled: Boolean) {
        context.dataStore.edit { it[PRIVACY_KEYBOARD_KEY] = enabled }
    }
    suspend fun saveMaxHammingDistance(distance: Int) {
        context.dataStore.edit { it[MAX_HAMMING_DISTANCE_KEY] = distance }
    }
    suspend fun saveFileDomain(domain: String) {
        context.dataStore.edit { it[FILE_DOMAIN_KEY] = domain }
    }
    suspend fun saveTagDomain(domain: String) {
        context.dataStore.edit { it[TAG_DOMAIN_KEY] = domain }
    }
    suspend fun saveBackupDestinationDomain(domain: String) {
        context.dataStore.edit { it[BACKUP_DESTINATION_DOMAIN_KEY] = domain }
    }

    val apiKeyFlow: Flow<String> = context.dataStore.data.map { it[API_KEY] ?: "" }
    val serverIpFlow: Flow<String> = context.dataStore.data.map { it[SERVER_IP] ?: "127.0.0.1" }
    val serverPortFlow: Flow<String> = context.dataStore.data.map { it[PORT_KEY] ?: "45869" }
    val darkModeFlow: Flow<Boolean> = context.dataStore.data.map { it[DARK_MODE_KEY] ?: true }
    val gridColumnsFlow: Flow<Int> = context.dataStore.data.map { it[GRID_COLUMNS_KEY] ?: 3 }

    val hidePdfFlow: Flow<Boolean> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { it[HIDE_PDF_KEY] ?: false }

    val hidePsdFlow: Flow<Boolean> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { it[HIDE_PSD_KEY] ?: false }

    val hideZipFlow: Flow<Boolean> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { it[HIDE_ZIP_KEY] ?: false }
    val hideVideosFlow: Flow<Boolean> = context.dataStore.data.map { it[HIDE_VIDEOS_KEY] ?: false }
    val gifAutoLoopFlow: Flow<Boolean> = context.dataStore.data.map { it[GIF_AUTO_LOOP_KEY] ?: true }
    val visibleCategoriesFlow: Flow<Set<String>> = context.dataStore.data.map { it[VISIBLE_CATEGORIES_KEY] ?: DEFAULT_CATEGORIES }
    val privacyKeyboardFlow: Flow<Boolean> = context.dataStore.data.map { it[PRIVACY_KEYBOARD_KEY] ?: true }
    val maxHammingDistanceFlow: Flow<Int> = context.dataStore.data.map { it[MAX_HAMMING_DISTANCE_KEY] ?: 4 }
    val fileDomainFlow: Flow<String> = context.dataStore.data.map { it[FILE_DOMAIN_KEY] ?: "my files" }
    val tagDomainFlow: Flow<String> = context.dataStore.data.map { it[TAG_DOMAIN_KEY] ?: "my tags" }
    val backupDestinationDomainFlow: Flow<String> = context.dataStore.data.map { it[BACKUP_DESTINATION_DOMAIN_KEY] ?: "my files" }
}