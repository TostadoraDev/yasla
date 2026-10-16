package xyz.tosdev.yetasimplelyicsapp.widget

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition

class PreferencesLyricsWidgetConfigurationRepository(
    private val context: Context,
) : LyricsWidgetConfigurationRepository {
    private val manager = GlanceAppWidgetManager(context)

    override suspend fun load(appWidgetId: Int): LyricsWidgetConfig =
        LyricsWidgetState.read(context, manager.getGlanceIdBy(appWidgetId)).config

    override suspend fun save(appWidgetId: Int, config: LyricsWidgetConfig) {
        LyricsWidgetUpdater.updateConfiguration(context, appWidgetId, config)
    }

    override suspend fun clear(appWidgetId: Int) {
        LyricsWidgetState.clear(context, manager.getGlanceIdBy(appWidgetId))
    }
}

internal data class StoredLyricsWidgetState(
    val config: LyricsWidgetConfig,
    val snapshot: LyricsWidgetSnapshot,
    val renderedSignature: String,
)

internal object LyricsWidgetState {
    private val layoutKey = stringPreferencesKey("layout")
    private val contentModeKey = stringPreferencesKey("content_mode")
    private val showTranslationKey = booleanPreferencesKey("show_translation")
    private val showControlsKey = booleanPreferencesKey("show_controls")
    private val backgroundOpacityKey = intPreferencesKey("background_opacity")
    private val textScaleKey = floatPreferencesKey("text_scale")
    private val cornerRadiusKey = intPreferencesKey("corner_radius")
    private val primaryColorHexKey = stringPreferencesKey("primary_color_hex")
    private val useTitleAsFallbackKey = booleanPreferencesKey("use_title_fallback")
    private val verticalBiasKey = floatPreferencesKey("vertical_bias")
    private val idleImageSourceKey = stringPreferencesKey("idle_image_source")
    private val idleImagePathKey = stringPreferencesKey("idle_image_path")
    private val idleImageScaleKey = stringPreferencesKey("idle_image_scale")
    private val trackKey = stringPreferencesKey("track_key")
    private val titleKey = stringPreferencesKey("title")
    private val artistKey = stringPreferencesKey("artist")
    private val previousLineIdKey = stringPreferencesKey("previous_line_id")
    private val previousLineKey = stringPreferencesKey("previous_line")
    private val activeLineIdKey = stringPreferencesKey("active_line_id")
    private val activeLineKey = stringPreferencesKey("active_line")
    private val nextLineIdKey = stringPreferencesKey("next_line_id")
    private val nextLineKey = stringPreferencesKey("next_line")
    private val translationKey = stringPreferencesKey("translation")
    private val activeWordIdKey = stringPreferencesKey("active_word_id")
    private val activeWordKey = stringPreferencesKey("active_word")
    private val reliableWordSyncKey = booleanPreferencesKey("reliable_word_sync")
    private val syncTypeKey = stringPreferencesKey("sync_type")
    private val lyricsLanguageTagKey = stringPreferencesKey("lyrics_language_tag")
    private val isPlayingKey = booleanPreferencesKey("is_playing")
    private val capturedAtKey = longPreferencesKey("captured_at_elapsed_ms")
    private val presentationStateKey = stringPreferencesKey("presentation_state")
    private val renderedSignatureKey = stringPreferencesKey("rendered_signature")

    suspend fun read(context: Context, glanceId: GlanceId): StoredLyricsWidgetState {
        val preferences = runCatching {
            getAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId)
        }.getOrElse { emptyPreferences() }
        return preferences.toStoredState()
    }

    suspend fun saveConfig(
        context: Context,
        glanceId: GlanceId,
        config: LyricsWidgetConfig,
    ) {
        updateAppWidgetState(context, glanceId) { preferences ->
            preferences[layoutKey] = config.layout.name
            preferences[contentModeKey] = config.contentMode.name
            preferences[showTranslationKey] = config.showTranslation
            preferences[showControlsKey] = config.showPlaybackControls
            preferences[backgroundOpacityKey] = config.backgroundOpacityPercent.coerceIn(0, 100)
            preferences[textScaleKey] = config.textScale
            preferences[cornerRadiusKey] = config.cornerRadius
            config.primaryColorHex?.let { preferences[primaryColorHexKey] = it }
                ?: preferences.remove(primaryColorHexKey)
            preferences[useTitleAsFallbackKey] = config.useTitleAsFallback
            preferences[verticalBiasKey] = config.verticalBias
            preferences[idleImageSourceKey] = config.idleImageSource.name
            config.idleImagePath?.let { preferences[idleImagePathKey] = it }
                ?: preferences.remove(idleImagePathKey)
            preferences[idleImageScaleKey] = config.idleImageScale.name
            preferences[renderedSignatureKey] = ""
        }
    }

    /**
     * Atomically stores the latest display snapshot and returns the signature that still needs to
     * reach the launcher. The rendered signature is committed separately, after Glance succeeds,
     * so a transient update failure remains retryable.
     */
    suspend fun saveSnapshotIfRenderNeeded(
        context: Context,
        glanceId: GlanceId,
        snapshot: LyricsWidgetSnapshot,
        sizeClasses: Set<WidgetSizeClass>,
    ): String? {
        var pendingSignature: String? = null
        updateAppWidgetState(context, glanceId) { preferences ->
            val storedState = preferences.toStoredState()
            val config = storedState.config
            val safeSnapshot = snapshot.sanitized()
            val signature = safeSnapshot.lineRenderSignature(config, sizeClasses)
            if (shouldRenderWidget(
                    preferences[renderedSignatureKey].orEmpty(),
                    safeSnapshot,
                    config,
                    sizeClasses,
                )
            ) {
                pendingSignature = signature
            }
            if (storedState.snapshot.storedSnapshotSignature() != safeSnapshot.storedSnapshotSignature()) {
                preferences.writeSnapshot(safeSnapshot)
            }
        }
        return pendingSignature
    }

    /** One coalesced transaction for the native current-word fast path. */
    suspend fun persistDeliveredSnapshot(
        context: Context,
        glanceId: GlanceId,
        snapshot: LyricsWidgetSnapshot,
        signature: String,
        sizeClasses: Set<WidgetSizeClass>,
        shouldPersist: () -> Boolean,
    ) {
        updateAppWidgetState(context, glanceId) { preferences ->
            if (!shouldPersist()) return@updateAppWidgetState
            val safeSnapshot = snapshot.sanitized()
            preferences.writeSnapshot(safeSnapshot)
            val state = preferences.toStoredState()
            if (safeSnapshot.lineRenderSignature(state.config, sizeClasses) == signature) {
                preferences[renderedSignatureKey] = signature
            }
        }
    }

    suspend fun markRendered(
        context: Context,
        glanceId: GlanceId,
        signature: String,
        sizeClasses: Set<WidgetSizeClass>,
    ) {
        updateAppWidgetState(context, glanceId) { preferences ->
            val state = preferences.toStoredState()
            if (state.snapshot.lineRenderSignature(state.config, sizeClasses) == signature) {
                preferences[renderedSignatureKey] = signature
            }
        }
    }

    suspend fun clear(context: Context, glanceId: GlanceId) {
        // Advance the in-process generation before clearing durable state. A deferred writer that
        // was queued by the previous generation must not be able to recreate the deleted snapshot.
        runCatching { GlanceAppWidgetManager(context).getAppWidgetId(glanceId) }
            .getOrNull()
            ?.let(LyricsWidgetUpdater::invalidate)
        updateAppWidgetState(context, glanceId) { it.clear() }
    }

    fun fromPreferences(preferences: Preferences): StoredLyricsWidgetState =
        preferences.toStoredState()

    private fun Preferences.toStoredState(): StoredLyricsWidgetState {
        val layout = this[layoutKey]
            ?.let { stored -> LyricsWidgetLayout.entries.firstOrNull { it.name == stored } }
            ?: LyricsWidgetLayout.TWO_LINES
        val contentMode = this[contentModeKey]
            ?.let { stored -> LyricsWidgetContentMode.entries.firstOrNull { it.name == stored } }
            ?: LyricsWidgetContentMode.AUTO
        val config = LyricsWidgetConfig(
            layout = layout,
            contentMode = contentMode,
            showTranslation = this[showTranslationKey] ?: true,
            showPlaybackControls = this[showControlsKey] ?: false,
            backgroundOpacityPercent = (this[backgroundOpacityKey] ?: 92).coerceIn(0, 100),
            textScale = (this[textScaleKey] ?: 1.0f).coerceIn(0.5f, 2.0f),
            cornerRadius = (this[cornerRadiusKey] ?: 24).coerceIn(0, 64),
            primaryColorHex = this[primaryColorHexKey],
            useTitleAsFallback = this[useTitleAsFallbackKey] ?: true,
            verticalBias = (this[verticalBiasKey] ?: 0.5f).coerceIn(0f, 1f),
            idleImageSource = this[idleImageSourceKey]
                ?.let { stored -> WidgetIdleImageSource.entries.firstOrNull { it.name == stored } }
                ?: WidgetIdleImageSource.YASLA_DEFAULT,
            idleImagePath = this[idleImagePathKey],
            idleImageScale = this[idleImageScaleKey]
                ?.let { stored -> WidgetIdleImageScale.entries.firstOrNull { it.name == stored } }
                ?: WidgetIdleImageScale.COVER,
        )
        return StoredLyricsWidgetState(
            config = config,
            snapshot = LyricsWidgetSnapshot(
                trackKey = this[trackKey].orEmpty(),
                title = this[titleKey].orEmpty(),
                artist = this[artistKey].orEmpty(),
                previousLineId = this[previousLineIdKey].orEmpty(),
                previousLine = this[previousLineKey].orEmpty(),
                activeLineId = this[activeLineIdKey].orEmpty(),
                activeLine = this[activeLineKey].orEmpty(),
                nextLineId = this[nextLineIdKey].orEmpty(),
                nextLine = this[nextLineKey].orEmpty(),
                translation = this[translationKey].orEmpty(),
                activeWordId = this[activeWordIdKey].orEmpty(),
                activeWord = this[activeWordKey].orEmpty(),
                hasReliableWordSync = this[reliableWordSyncKey] ?: false,
                syncType = this[syncTypeKey]
                    ?.let { stored ->
                        WidgetLyricsSyncType.entries.firstOrNull { it.name == stored }
                    }
                    ?: if (this[reliableWordSyncKey] == true) {
                        WidgetLyricsSyncType.WORD
                    } else {
                        WidgetLyricsSyncType.PLAIN
                    },
                lyricsLanguageTag = this[lyricsLanguageTagKey].orEmpty(),
                isPlaying = this[isPlayingKey] ?: false,
                capturedAtElapsedMs = this[capturedAtKey] ?: 0L,
                presentationState = this[presentationStateKey]
                    ?.let { stored ->
                        WidgetPresentationState.entries.firstOrNull { it.name == stored }
                    }
                    ?: WidgetPresentationState.LYRICS_AVAILABLE,
            ),
            renderedSignature = this[renderedSignatureKey].orEmpty(),
        )
    }

    private fun LyricsWidgetSnapshot.sanitized(): LyricsWidgetSnapshot = copy(
        trackKey = trackKey.bounded(256),
        title = title.bounded(160),
        artist = artist.bounded(160),
        previousLineId = previousLineId.bounded(160),
        previousLine = previousLine.bounded(500),
        activeLineId = activeLineId.bounded(160),
        activeLine = activeLine.bounded(500),
        nextLineId = nextLineId.bounded(160),
        nextLine = nextLine.bounded(500),
        translation = translation.bounded(500),
        activeWordId = activeWordId.bounded(160),
        // A semantic word may be very long. Do not truncate it at an arbitrary UTF-16 boundary:
        // the balanced visual wrapper owns the display split and must retain every grapheme.
        activeWord = activeWord.trim(),
        lyricsLanguageTag = lyricsLanguageTag.bounded(64),
    )

    private fun androidx.datastore.preferences.core.MutablePreferences.writeSnapshot(
        snapshot: LyricsWidgetSnapshot,
    ) {
        this[trackKey] = snapshot.trackKey
        this[titleKey] = snapshot.title
        this[artistKey] = snapshot.artist
        this[previousLineIdKey] = snapshot.previousLineId
        this[previousLineKey] = snapshot.previousLine
        this[activeLineIdKey] = snapshot.activeLineId
        this[activeLineKey] = snapshot.activeLine
        this[nextLineIdKey] = snapshot.nextLineId
        this[nextLineKey] = snapshot.nextLine
        this[translationKey] = snapshot.translation
        this[activeWordIdKey] = snapshot.activeWordId
        this[activeWordKey] = snapshot.activeWord
        this[reliableWordSyncKey] = snapshot.hasReliableWordSync
        this[syncTypeKey] = snapshot.syncType.name
        this[lyricsLanguageTagKey] = snapshot.lyricsLanguageTag
        this[isPlayingKey] = snapshot.isPlaying
        this[capturedAtKey] = snapshot.capturedAtElapsedMs.coerceAtLeast(0L)
        this[presentationStateKey] = snapshot.presentationState.name
    }

    private fun String.bounded(maxChars: Int): String =
        trim().take(maxChars)
}
