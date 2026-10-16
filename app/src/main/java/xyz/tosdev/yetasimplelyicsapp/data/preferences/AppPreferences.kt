package xyz.tosdev.yetasimplelyicsapp.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import java.security.MessageDigest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private const val DATA_STORE_NAME = "app_preferences"
private const val PROVIDER_SEPARATOR = "\u001F"
private val Context.lyricsDataStore: DataStore<Preferences> by preferencesDataStore(DATA_STORE_NAME)

class AppPreferences(context: Context) {
    private val dataStore = context.applicationContext.lyricsDataStore

    val lyricsNotificationEnabled: Flow<Boolean> = dataStore.safeData.map {
        it[LYRICS_NOTIFICATION_ENABLED] ?: false
    }

    suspend fun setLyricsNotificationEnabled(enabled: Boolean) {
        dataStore.edit { it[LYRICS_NOTIFICATION_ENABLED] = enabled }
    }

    val providerPriority: Flow<List<String>> = dataStore.safeData.map { preferences ->
        ProviderPriorityCodec.decode(preferences[PROVIDER_PRIORITY])
            .ifEmpty { DEFAULT_PROVIDER_PRIORITY }
    }

    suspend fun setProviderPriority(providerIds: List<String>) {
        val normalized = providerIds.map(String::trim).filter(String::isNotEmpty).distinct()
        require(normalized.isNotEmpty()) { "provider priority must not be empty" }
        dataStore.edit { it[PROVIDER_PRIORITY] = ProviderPriorityCodec.encode(normalized) }
    }

    fun lyricsOffset(trackKey: String): Flow<Long> {
        return lyricsOffsetOverride(trackKey).map { it ?: 0L }
    }

    fun lyricsOffsetOverride(trackKey: String): Flow<Long?> {
        require(trackKey.isNotBlank()) { "trackKey must not be blank" }
        val key = longPreferencesKey("lyrics_offset_${stableKey(trackKey)}")
        return dataStore.safeData.map { it[key] }
    }

    suspend fun setLyricsOffset(trackKey: String, offsetMs: Long) {
        require(trackKey.isNotBlank()) { "trackKey must not be blank" }
        val key = longPreferencesKey("lyrics_offset_${stableKey(trackKey)}")
        dataStore.edit { it[key] = offsetMs }
    }

    suspend fun clearLyricsOffset(trackKey: String) {
        require(trackKey.isNotBlank()) { "trackKey must not be blank" }
        val key = longPreferencesKey("lyrics_offset_${stableKey(trackKey)}")
        dataStore.edit { it.remove(key) }
    }

    private val DataStore<Preferences>.safeData: Flow<Preferences>
        get() = data.catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }

    private fun stableKey(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }

    companion object {
        val DEFAULT_PROVIDER_PRIORITY = listOf("local", "lrclib")
        private val LYRICS_NOTIFICATION_ENABLED = booleanPreferencesKey("lyrics_notification_enabled")
        private val PROVIDER_PRIORITY = stringPreferencesKey("provider_priority")
    }
}

internal object ProviderPriorityCodec {
    fun encode(providerIds: List<String>): String = providerIds.joinToString(PROVIDER_SEPARATOR)

    fun decode(value: String?): List<String> =
        value.orEmpty()
            .split(PROVIDER_SEPARATOR)
            .map(String::trim)
            .filter(String::isNotEmpty)
            .distinct()
}
