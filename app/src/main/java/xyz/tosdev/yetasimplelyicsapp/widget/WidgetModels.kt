package xyz.tosdev.yetasimplelyicsapp.widget

import xyz.tosdev.yetasimplelyicsapp.ui.LyricsLoadState
import xyz.tosdev.yetasimplelyicsapp.ui.LyricsTimingMode
import xyz.tosdev.yetasimplelyicsapp.ui.NowPlayingUiState

enum class LyricsWidgetLayout {
    MINIMAL,
    TWO_LINES,
    TRANSPARENT,
    KARAOKE_CARD,
    LARGE,
    COMPACT_PLAYER,
    WORD_FOCUS,
}

enum class LyricsWidgetContentMode {
    AUTO,
    CURRENT_WORD,
    CURRENT_LINE,
    CURRENT_NEXT,
    PREVIOUS_CURRENT_NEXT,
    KARAOKE,
    DESKTOP,
}

enum class WidgetLyricsSyncType {
    PLAIN,
    LINE,
    WORD,
}

enum class WidgetPresentationState {
    NO_PLAYBACK,
    SEARCHING_LYRICS,
    LYRICS_AVAILABLE,
    LYRICS_UNAVAILABLE,
    ERROR,
}

enum class WidgetIdleImageSource {
    YASLA_DEFAULT,
    CUSTOM,
}

enum class WidgetIdleImageScale {
    COVER,
    FIT,
}

internal enum class ResolvedWidgetContent {
    CURRENT_WORD,
    CURRENT_LINE,
    CURRENT_NEXT,
    PREVIOUS_CURRENT_NEXT,
    KARAOKE,
    DESKTOP,
}

data class LyricsWidgetConfig(
    val layout: LyricsWidgetLayout = LyricsWidgetLayout.TWO_LINES,
    val contentMode: LyricsWidgetContentMode = LyricsWidgetContentMode.AUTO,
    val showTranslation: Boolean = true,
    val showPlaybackControls: Boolean = false,
    val backgroundOpacityPercent: Int = 92,
    val textScale: Float = 1.0f,
    val cornerRadius: Int = 24,
    val primaryColorHex: String? = null, // Custom accent color
    val useTitleAsFallback: Boolean = true,
    val verticalBias: Float = 0.5f, // Top (0) to Bottom (1)
    val idleImageSource: WidgetIdleImageSource = WidgetIdleImageSource.YASLA_DEFAULT,
    val idleImagePath: String? = null,
    val idleImageScale: WidgetIdleImageScale = WidgetIdleImageScale.COVER,
) {
    init {
        require(backgroundOpacityPercent in 0..100) {
            "backgroundOpacityPercent must be between 0 and 100"
        }
        require(textScale in 0.5f..2.0f)
        require(cornerRadius in 0..64)
        require(verticalBias in 0f..1f)
    }
}

internal fun LyricsWidgetConfig.resolveContent(
    snapshot: LyricsWidgetSnapshot,
    sizeClass: WidgetSizeClass = WidgetSizeClass.COMPACT,
): ResolvedWidgetContent = when (contentMode) {
    LyricsWidgetContentMode.CURRENT_LINE -> ResolvedWidgetContent.CURRENT_LINE
    LyricsWidgetContentMode.CURRENT_NEXT -> ResolvedWidgetContent.CURRENT_NEXT
    LyricsWidgetContentMode.PREVIOUS_CURRENT_NEXT ->
        ResolvedWidgetContent.PREVIOUS_CURRENT_NEXT
    LyricsWidgetContentMode.KARAOKE -> ResolvedWidgetContent.KARAOKE
    LyricsWidgetContentMode.DESKTOP -> ResolvedWidgetContent.DESKTOP
    LyricsWidgetContentMode.CURRENT_WORD ->
        if (snapshot.hasUsableWordSync) {
            ResolvedWidgetContent.CURRENT_WORD
        } else {
            ResolvedWidgetContent.CURRENT_LINE
        }

    LyricsWidgetContentMode.AUTO -> resolveAutoContent(snapshot, sizeClass)
}

private fun LyricsWidgetConfig.resolveAutoContent(
    snapshot: LyricsWidgetSnapshot,
    sizeClass: WidgetSizeClass,
): ResolvedWidgetContent {
    if (snapshot.effectiveSyncType == WidgetLyricsSyncType.PLAIN) {
        return ResolvedWidgetContent.CURRENT_LINE
    }
    if (snapshot.effectiveSyncType == WidgetLyricsSyncType.WORD && snapshot.hasUsableWordSync) {
        return when (sizeClass) {
            WidgetSizeClass.COMPACT -> ResolvedWidgetContent.CURRENT_WORD
            WidgetSizeClass.MEDIUM -> when (layout) {
                LyricsWidgetLayout.MINIMAL,
                LyricsWidgetLayout.TRANSPARENT,
                LyricsWidgetLayout.WORD_FOCUS,
                    -> ResolvedWidgetContent.CURRENT_WORD

                else -> ResolvedWidgetContent.CURRENT_NEXT
            }

            WidgetSizeClass.EXPANDED -> when (layout) {
                LyricsWidgetLayout.MINIMAL,
                LyricsWidgetLayout.WORD_FOCUS,
                    -> ResolvedWidgetContent.CURRENT_WORD

                LyricsWidgetLayout.TRANSPARENT -> ResolvedWidgetContent.DESKTOP
                LyricsWidgetLayout.LARGE -> ResolvedWidgetContent.PREVIOUS_CURRENT_NEXT
                LyricsWidgetLayout.KARAOKE_CARD -> ResolvedWidgetContent.KARAOKE
                LyricsWidgetLayout.TWO_LINES,
                LyricsWidgetLayout.COMPACT_PLAYER,
                    -> ResolvedWidgetContent.CURRENT_NEXT
            }
        }
    }

    return when (sizeClass) {
        WidgetSizeClass.COMPACT -> ResolvedWidgetContent.CURRENT_LINE
        WidgetSizeClass.MEDIUM -> when (layout) {
            LyricsWidgetLayout.MINIMAL,
            LyricsWidgetLayout.WORD_FOCUS,
                -> ResolvedWidgetContent.CURRENT_LINE

            LyricsWidgetLayout.TRANSPARENT -> ResolvedWidgetContent.DESKTOP
            else -> ResolvedWidgetContent.CURRENT_NEXT
        }

        WidgetSizeClass.EXPANDED -> when (layout) {
            LyricsWidgetLayout.MINIMAL,
            LyricsWidgetLayout.WORD_FOCUS,
                -> ResolvedWidgetContent.CURRENT_LINE

            LyricsWidgetLayout.TRANSPARENT -> ResolvedWidgetContent.DESKTOP
            LyricsWidgetLayout.LARGE -> ResolvedWidgetContent.PREVIOUS_CURRENT_NEXT
            LyricsWidgetLayout.KARAOKE_CARD -> ResolvedWidgetContent.KARAOKE
            LyricsWidgetLayout.TWO_LINES,
            LyricsWidgetLayout.COMPACT_PLAYER,
                -> ResolvedWidgetContent.CURRENT_NEXT
        }
    }
}

internal enum class WidgetSizeClass {
    COMPACT,
    MEDIUM,
    EXPANDED,
}

internal data class WidgetRenderPolicy(
    val sizeClass: WidgetSizeClass,
    val layout: LyricsWidgetLayout,
    val showTranslation: Boolean,
    val showPlaybackControls: Boolean,
)

internal fun widgetSizeClass(widthDp: Float, heightDp: Float): WidgetSizeClass = when {
    heightDp < 88f || widthDp < 160f -> WidgetSizeClass.COMPACT
    heightDp < 152f || widthDp < 230f -> WidgetSizeClass.MEDIUM
    else -> WidgetSizeClass.EXPANDED
}

/**
 * Adapts content to the exact rectangle supplied by the widget host. The order is deliberate:
 * secondary content and controls disappear before the configured text scale is touched.
 */
internal fun LyricsWidgetConfig.renderPolicy(
    widthDp: Float,
    heightDp: Float,
): WidgetRenderPolicy {
    val sizeClass = widgetSizeClass(widthDp, heightDp)
    val responsiveLayout = when (sizeClass) {
        WidgetSizeClass.COMPACT -> when (layout) {
            LyricsWidgetLayout.WORD_FOCUS -> LyricsWidgetLayout.WORD_FOCUS
            else -> LyricsWidgetLayout.MINIMAL
        }

        WidgetSizeClass.MEDIUM -> when (layout) {
            LyricsWidgetLayout.LARGE,
            LyricsWidgetLayout.KARAOKE_CARD,
                -> LyricsWidgetLayout.TWO_LINES

            else -> layout
        }

        WidgetSizeClass.EXPANDED -> layout
    }
    return WidgetRenderPolicy(
        sizeClass = sizeClass,
        layout = responsiveLayout,
        showTranslation = showTranslation && sizeClass == WidgetSizeClass.EXPANDED,
        showPlaybackControls = showPlaybackControls && sizeClass == WidgetSizeClass.EXPANDED,
    )
}

data class LyricsWidgetSnapshot(
    val trackKey: String = "",
    val title: String = "",
    val artist: String = "",
    val previousLineId: String = "",
    val previousLine: String = "",
    val activeLineId: String = "",
    val activeLine: String = "",
    val nextLineId: String = "",
    val nextLine: String = "",
    val translation: String = "",
    val activeWordId: String = "",
    val activeWord: String = "",
    val hasReliableWordSync: Boolean = false,
    val syncType: WidgetLyricsSyncType = WidgetLyricsSyncType.PLAIN,
    val lyricsLanguageTag: String = "",
    val isPlaying: Boolean = false,
    val capturedAtElapsedMs: Long = 0L,
    val presentationState: WidgetPresentationState = WidgetPresentationState.LYRICS_AVAILABLE,
    // Transient scheduler evidence and one-word lookahead. These values are deliberately
    // excluded from render/storage signatures: timing can advance without changing pixels.
    val activeWordSourceStartMs: Long = -1L,
    val activeWordScheduledAtElapsedMs: Long = -1L,
    val renderRequestedAtElapsedMs: Long = -1L,
    val nextWordId: String = "",
    val nextWord: String = "",
    val nextWordSourceStartMs: Long = -1L,
    val nextWordScheduledAtElapsedMs: Long = -1L,
) {
    val hasTrack: Boolean
        get() = trackKey.isNotBlank()

    fun fallbackLabel(): String = when {
        title.isNotBlank() && artist.isNotBlank() -> "$title - $artist"
        title.isNotBlank() -> title
        else -> ""
    }

    val resolvedPresentationState: WidgetPresentationState
        get() = if (hasTrack) presentationState else WidgetPresentationState.NO_PLAYBACK

    internal val effectiveSyncType: WidgetLyricsSyncType
        get() = if (hasReliableWordSync) WidgetLyricsSyncType.WORD else syncType

    internal val hasUsableWordSync: Boolean
        get() = effectiveSyncType == WidgetLyricsSyncType.WORD &&
            activeWordId.isNotBlank() && activeWord.isNotBlank()

    /** Widgets update at semantic word or line boundaries, never at timed-segment boundaries. */
    internal fun lineRenderSignature(
        config: LyricsWidgetConfig,
        sizeClasses: Set<WidgetSizeClass> = setOf(WidgetSizeClass.COMPACT),
    ): String = buildString {
        append(trackKey)
        append('|')
        append(resolvedPresentationState.name)
        val resolvedContents = sizeClasses.ifEmpty { setOf(WidgetSizeClass.COMPACT) }
            .mapTo(linkedSetOf()) { config.resolveContent(this@LyricsWidgetSnapshot, it) }
        resolvedContents.sortedBy(ResolvedWidgetContent::ordinal).forEach { resolved ->
            append('|')
            appendContentSignature(resolved, config)
        }
        if (
            config.showTranslation && resolvedContents.any {
                it == ResolvedWidgetContent.CURRENT_NEXT ||
                    it == ResolvedWidgetContent.PREVIOUS_CURRENT_NEXT ||
                    it == ResolvedWidgetContent.KARAOKE ||
                    it == ResolvedWidgetContent.DESKTOP
            }
        ) {
            append("|translation|")
            append(translation)
        }
        if (resolvedContents.any {
                it == ResolvedWidgetContent.CURRENT_NEXT ||
                    it == ResolvedWidgetContent.KARAOKE ||
                    (it == ResolvedWidgetContent.CURRENT_LINE &&
                        config.layout == LyricsWidgetLayout.COMPACT_PLAYER)
            } || config.showPlaybackControls
        ) {
            append("|playing|")
            append(isPlaying)
        }
        if (resolvedContents.any {
                it == ResolvedWidgetContent.PREVIOUS_CURRENT_NEXT ||
                    (it == ResolvedWidgetContent.CURRENT_LINE &&
                        config.layout == LyricsWidgetLayout.COMPACT_PLAYER)
            }
        ) {
            append("|metadata|")
            append(title)
            append('|')
            append(artist)
        }
        append('|')
        append(config.contentMode.name)
        append('|')
        append(config.layout.name)
        append('|')
        append(config.showTranslation)
        append('|')
        append(config.showPlaybackControls)
        append('|')
        append(config.backgroundOpacityPercent)
        append('|')
        append(config.textScale)
        append('|')
        append(config.cornerRadius)
        append('|')
        append(config.primaryColorHex.orEmpty())
        append('|')
        append(config.verticalBias)
        append('|')
        append(config.idleImageSource.name)
        append('|')
        append(config.idleImagePath.orEmpty())
        append('|')
        append(config.idleImageScale.name)
        append('|')
        append(effectiveSyncType.name)
    }

    private fun StringBuilder.appendContentSignature(
        resolved: ResolvedWidgetContent,
        config: LyricsWidgetConfig,
    ) {
        when (resolved) {
            ResolvedWidgetContent.CURRENT_WORD -> {
                append("WORD|")
                append(activeWordId)
                append('|')
                append(activeWord)
                append('|')
                append(lyricsLanguageTag)
            }

            ResolvedWidgetContent.CURRENT_LINE -> {
                append("LINE|")
                append(activeLineId)
                append('|')
                append(activeLine.ifBlank {
                    if (config.useTitleAsFallback) fallbackLabel() else ""
                })
            }

            ResolvedWidgetContent.CURRENT_NEXT,
            ResolvedWidgetContent.DESKTOP,
                -> {
                    append("CURRENT_NEXT|")
                    append(activeLineId)
                    append('|')
                    append(activeLine.ifBlank {
                        if (config.useTitleAsFallback) fallbackLabel() else ""
                    })
                    append('|')
                    append(nextLineId)
                    append('|')
                    append(nextLine)
                }

            ResolvedWidgetContent.PREVIOUS_CURRENT_NEXT -> {
                append("CONTEXT|")
                append(previousLineId)
                append('|')
                append(previousLine)
                append('|')
                append(activeLineId)
                append('|')
                append(activeLine.ifBlank {
                    if (config.useTitleAsFallback) fallbackLabel() else ""
                })
                append('|')
                append(nextLineId)
                append('|')
                append(nextLine)
            }

            ResolvedWidgetContent.KARAOKE -> {
                // AppWidget cannot animate at frame rate. Karaoke refreshes at semantic line
                // boundaries while keeping its distinct visual preset.
                append("KARAOKE|")
                append(activeLineId)
                append('|')
                append(activeLine.ifBlank {
                    if (config.useTitleAsFallback) fallbackLabel() else ""
                })
            }
        }
    }

    fun dataSignature(): String = buildString {
        append(trackKey)
        append('|')
        append(title)
        append('|')
        append(artist)
        append('|')
        append(previousLineId)
        append('|')
        append(activeLine)
        append('|')
        append(previousLine)
        append('|')
        append(nextLineId)
        append('|')
        append(nextLine)
        append('|')
        append(translation)
        append('|')
        append(isPlaying)
        append('|')
        append(activeLineId)
    }

    internal fun storedSnapshotSignature(): String = buildString {
        append(dataSignature())
        append('|')
        append(activeWordId)
        append('|')
        append(activeWord)
        append('|')
        append(hasReliableWordSync)
        append('|')
        append(syncType.name)
        append('|')
        append(lyricsLanguageTag)
        append('|')
        append(presentationState.name)
    }
}

internal fun NowPlayingUiState.toWidgetPresentationState(): WidgetPresentationState = when {
    !hasActiveSession -> WidgetPresentationState.NO_PLAYBACK
    lyrics.isNotEmpty() || lyricsLoadState == LyricsLoadState.AVAILABLE ->
        WidgetPresentationState.LYRICS_AVAILABLE
    lyricsLoadState == LyricsLoadState.NOT_FOUND -> WidgetPresentationState.LYRICS_UNAVAILABLE
    lyricsLoadState == LyricsLoadState.ERROR -> WidgetPresentationState.ERROR
    else -> WidgetPresentationState.SEARCHING_LYRICS
}

internal fun shouldRenderWidget(
    renderedSignature: String,
    snapshot: LyricsWidgetSnapshot,
    config: LyricsWidgetConfig,
    sizeClasses: Set<WidgetSizeClass> = setOf(WidgetSizeClass.COMPACT),
): Boolean = renderedSignature != snapshot.lineRenderSignature(config, sizeClasses)

enum class WidgetPlaybackCommand(val wireValue: String) {
    PREVIOUS("previous"),
    PLAY_PAUSE("play_pause"),
    NEXT("next"),
}

interface LyricsWidgetConfigurationRepository {
    suspend fun load(appWidgetId: Int): LyricsWidgetConfig
    suspend fun save(appWidgetId: Int, config: LyricsWidgetConfig)
    suspend fun clear(appWidgetId: Int)
}
