package xyz.tosdev.yetasimplelyicsapp.ui

import androidx.compose.runtime.Immutable
import xyz.tosdev.yetasimplelyicsapp.domain.lyrics.LyricsAlignment
import xyz.tosdev.yetasimplelyicsapp.domain.lyrics.LyricsTextDirection

enum class AppDestination {
    NOW_PLAYING,
    LIBRARY,
    SEARCH,
    STUDIO,
    SETTINGS,
    EDITOR,
    OVERLAY,
    WIDGET,
}

enum class MotionLevel {
    OFF,
    REDUCED,
    NORMAL,
    ENHANCED,
}

enum class AppThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
}

enum class LyricsDisplayMode { FULL_LYRICS, CURRENT_WORD }
enum class CurrentWordAlignment { FOLLOW_SINGER, LEFT, CENTER, RIGHT }
enum class LyricsTimingMode { PLAIN, LINE_SYNCED, WORD_SYNCED }
enum class LyricsLoadState { IDLE, SEARCHING, AVAILABLE, NOT_FOUND, ERROR }

@Immutable
data class KaraokeSegmentUi(
    val id: String,
    val text: String,
    val startTimeMs: Long?,
    val endTimeMs: Long?,
)

@Immutable
data class KaraokeWordUi(
    val id: String,
    val text: String,
    val progress: Float = 0f,
    val startTimeMs: Long? = null,
    val endTimeMs: Long? = null,
    val segments: List<KaraokeSegmentUi> = emptyList(),
)

@Immutable
data class LyricLineUi(
    val id: String,
    val words: List<KaraokeWordUi>,
    val originalText: String? = null,
    val translation: String? = null,
    val isBackground: Boolean = false,
    val isActive: Boolean = false,
    val isPast: Boolean = false,
    val startTimeMs: Long? = null,
    val endTimeMs: Long? = null,
    val alignment: LyricsAlignment = LyricsAlignment.LEFT,
    val sourceAlignment: LyricsAlignment = LyricsAlignment.AUTO,
    val agentId: String? = null,
    val direction: LyricsTextDirection = LyricsTextDirection.AUTO,
) {
    val plainText: String
        get() = originalText ?: words.joinToString(separator = " ") { it.text }

    val timingMode: LyricsTimingMode
        get() = when {
            words.any { it.hasDetailedTiming } -> LyricsTimingMode.WORD_SYNCED
            startTimeMs != null && endTimeMs != null && endTimeMs > startTimeMs ->
                LyricsTimingMode.LINE_SYNCED

            else -> LyricsTimingMode.PLAIN
        }
}

val KaraokeWordUi.timedSegments: List<KaraokeSegmentUi>
    get() = segments.ifEmpty {
        if (startTimeMs != null && endTimeMs != null && endTimeMs > startTimeMs) {
            listOf(KaraokeSegmentUi(id, text, startTimeMs, endTimeMs))
        } else {
            emptyList()
        }
    }

val KaraokeWordUi.hasDetailedTiming: Boolean
    get() {
        val lexicalText = text.trim()
        return lexicalText.isNotEmpty() && !lexicalText.any(Char::isWhitespace) &&
                timedSegments.any { segment ->
                    segment.startTimeMs != null && segment.endTimeMs != null &&
                            segment.endTimeMs > segment.startTimeMs
                }
    }

@Immutable
data class NowPlayingUiState(
    val connectionLabel: String = "",
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val artworkSeed: Int = 0,
    val artworkUri: String? = null,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val hasActiveSession: Boolean = false,
    val isPlaying: Boolean = false,
    val canTogglePlayback: Boolean = false,
    val canSeek: Boolean = false,
    val canSkipPrevious: Boolean = false,
    val canSkipNext: Boolean = false,
    val sourceLabel: String? = null,
    val lyrics: List<LyricLineUi> = emptyList(),
    val lyricsLoadState: LyricsLoadState = LyricsLoadState.IDLE,
    val lyricsLoadMessage: String? = null,
    val activeLineId: String? = null,
    val activeWordId: String? = null,
    val offsetMs: Long = 0L,
    val isPreview: Boolean = false,
    val timeline: LyricsTimeline = LyricsTimeline(),
)

internal fun canTogglePlayback(
    isPlaying: Boolean,
    canPlay: Boolean,
    canPause: Boolean,
): Boolean = if (isPlaying) canPause else canPlay

@Immutable
data class LibraryItemUi(
    val id: String,
    val title: String,
    val artist: String,
    val detail: String,
    val hasWordSync: Boolean,
    val artworkUri: String? = null,
)

@Immutable
data class LibraryUiState(
    val recent: List<LibraryItemUi> = emptyList(),
    val favorites: List<LibraryItemUi> = emptyList(),
)

@Immutable
data class SearchResultUi(
    val id: String,
    val title: String,
    val artist: String,
    val provider: String,
    val syncLabel: String,
)

@Immutable
data class SearchUiState(
    val query: String = "",
    val isSearching: Boolean = false,
    val results: List<SearchResultUi> = emptyList(),
    val message: String? = null,
)

@Immutable
data class EditorUiState(
    val title: String = "",
    val artist: String = "",
    val rawLyrics: String = "",
    val offsetMs: Long = 0L,
    val unsavedChanges: Boolean = false,
)

@Immutable
data class OverlayUiState(
    val enabled: Boolean = false,
    val opacity: Float = 0.92f,
    val scale: Float = 1f,
    val mode: String = "COMPACT",
    val layout: String = "CURRENT_LINE",
    val currentWordAlignment: String = "CENTER",
    val preset: String = "MINIMAL",
    val alignment: String = "START",
    val clickThrough: Boolean = false,
    val hideWhenPaused: Boolean = true,
    val lockScreenMode: String = "OFF",
    val useLockScreenNotification: Boolean = false,
    val showTranslation: Boolean = true,
    val background: String = "TRANSPARENT",
    val cornerRadius: Float = 20f,
    val paddingDp: Float = 16f,
    val showBorder: Boolean = false,
    val softShadow: Boolean = true,
)

@Immutable
data class WidgetUiState(
    val style: WidgetStyle = WidgetStyle.COMPACT,
    val showArtwork: Boolean = true,
    val showTranslation: Boolean = false,
    val cornerRadius: Float = 24f,
)

enum class PreviewAlignment { START, CENTER, END }
enum class WidgetStyle { COMPACT, LYRICS, ARTWORK }

enum class AccessStatus {
    NOT_REQUESTED,
    GRANTED,
    DENIED,
    UNAVAILABLE,
}

@Immutable
data class SystemAccessUiState(
    val mediaSession: AccessStatus = AccessStatus.NOT_REQUESTED,
    val overlay: AccessStatus = AccessStatus.NOT_REQUESTED,
    val notifications: AccessStatus = AccessStatus.NOT_REQUESTED,
    val lyricsNotificationEnabled: Boolean = false,
    val widgetPickerAvailable: Boolean = true,
)

@Immutable
data class SettingsUiState(
    val themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    val dynamicColor: Boolean = false,
    val motionLevel: MotionLevel = MotionLevel.NORMAL,
    val translationLanguage: String = "Español",
    val preferWordSync: Boolean = true,
    val lyricsAlignment: String = "AUTO",
    val lyricsDisplayMode: LyricsDisplayMode = LyricsDisplayMode.FULL_LYRICS,
    val lyricsTextScale: Float = DEFAULT_LYRICS_TEXT_SCALE,
    val currentWordAlignment: CurrentWordAlignment = CurrentWordAlignment.CENTER,
    val currentWordFallbackToLine: Boolean = true,
    val keepScreenAwake: Boolean = true,
    val highContrastLyrics: Boolean = false,
    val karaokeStyle: KaraokeVisualStyle = KaraokeVisualStyle(),
    val languageTag: String = "en",
)

internal const val MIN_LYRICS_TEXT_SCALE = 0.75f
internal const val DEFAULT_LYRICS_TEXT_SCALE = 1f
internal const val MAX_LYRICS_TEXT_SCALE = 1.5f

internal fun normalizeLyricsTextScale(value: Float): Float =
    value.takeIf(Float::isFinite)
        ?.coerceIn(MIN_LYRICS_TEXT_SCALE, MAX_LYRICS_TEXT_SCALE)
        ?: DEFAULT_LYRICS_TEXT_SCALE

internal fun scaledLyricsSp(baseSp: Float, textScale: Float): Float =
    baseSp * normalizeLyricsTextScale(textScale)

@Immutable
data class LyricsAppUiState(
    val nowPlaying: NowPlayingUiState = NowPlayingUiState(),
    val library: LibraryUiState = LibraryUiState(),
    val search: SearchUiState = SearchUiState(),
    val editor: EditorUiState = EditorUiState(),
    val overlay: OverlayUiState = OverlayUiState(),
    val widget: WidgetUiState = WidgetUiState(),
    val widgetSnapshot: xyz.tosdev.yetasimplelyicsapp.widget.LyricsWidgetSnapshot = xyz.tosdev.yetasimplelyicsapp.widget.LyricsWidgetSnapshot(),
    val settings: SettingsUiState = SettingsUiState(),
    val systemAccess: SystemAccessUiState = SystemAccessUiState(),
)

data class LyricsAppActions(
    val onSearch: (String) -> Unit = {},
    val onOpenLibraryItem: (String) -> Unit = {},
    val onOpenSearchResult: (String) -> Unit = {},
    val onSkipPrevious: () -> Unit = {},
    val onTogglePlayback: () -> Unit = {},
    val onSkipNext: () -> Unit = {},
    val onSeek: (Float) -> Unit = {},
    val onSeekLine: (String) -> Unit = {},
    val onRetryLyrics: () -> Unit = {},
    val onOffsetChange: (Long) -> Unit = {},
    val onEditorSave: (EditorUiState) -> Unit = {},
    val onSettingsChange: (SettingsUiState) -> Unit = {},
    val onOverlayChange: (OverlayUiState) -> Unit = {},
    val onWidgetChange: (WidgetUiState) -> Unit = {},
    val onRequestMediaAccess: () -> Unit = {},
    val onRequestOverlayAccess: () -> Unit = {},
    val onImportLyrics: () -> Unit = {},
    val onExportLyrics: () -> Unit = {},
    val onExportTtml: () -> Unit = {},
    val onToggleLyricsNotification: (Boolean) -> Unit = {},
    val onOpenWidgetPicker: () -> Unit = {},
    val onLanguageChange: (String) -> Unit = {},
)
