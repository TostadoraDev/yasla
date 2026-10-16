package xyz.tosdev.yetasimplelyicsapp.overlay

import xyz.tosdev.yetasimplelyicsapp.ui.CurrentWordAlignment
import xyz.tosdev.yetasimplelyicsapp.ui.KaraokeVisualStyle
import xyz.tosdev.yetasimplelyicsapp.ui.LyricLineUi
import xyz.tosdev.yetasimplelyicsapp.ui.LyricsTimeline
import xyz.tosdev.yetasimplelyicsapp.ui.MotionLevel

enum class OverlayMode {
    COMPACT,
    EXPANDED,
}

enum class OverlayLayout {
    CURRENT_LINE,
    CURRENT_AND_NEXT,
    PREVIOUS_CURRENT_NEXT,
    CURRENT_WORD,
    KARAOKE,
    DESKTOP_LYRICS,
}

enum class OverlayPreset {
    MINIMAL,
    DESKTOP,
    APPLE_INSPIRED,
    KARAOKE,
    DUET,
    TRANSPARENT,
    GLASS,
    HIGH_CONTRAST,
}

enum class OverlayBackground {
    TEXT_ONLY,
    FROSTED,
    TRANSPARENT,
    SUBTLE_DARK,
    ARTWORK,
    PILL,
    CARD,
}

enum class OverlayAlignment {
    START,
    CENTER,
    END,
}

enum class LockScreenOverlayMode {
    OFF,
    MINIMAL,
    ALWAYS,
}

data class OverlayConfig(
    val mode: OverlayMode = OverlayMode.COMPACT,
    val layout: OverlayLayout = OverlayLayout.CURRENT_LINE,
    val currentWordAlignment: CurrentWordAlignment = CurrentWordAlignment.CENTER,
    val preset: OverlayPreset = OverlayPreset.MINIMAL,
    val background: OverlayBackground = OverlayBackground.TRANSPARENT,
    val alignment: OverlayAlignment = OverlayAlignment.CENTER,
    val locked: Boolean = false,
    val clickThrough: Boolean = false,
    val hideWhenPaused: Boolean = true,
    val lockScreenMode: LockScreenOverlayMode = LockScreenOverlayMode.OFF,
    val useLockScreenNotification: Boolean = false,
    val opacity: Float = 0.92f,
    val textScale: Float = 1f,
    val cornerRadius: Float = 20f,
    val paddingDp: Float = 16f,
    val showBorder: Boolean = false,
    val softShadow: Boolean = true,
) {
    fun normalized(): OverlayConfig = copy(
        opacity = opacity.coerceIn(0.25f, 1f),
        textScale = textScale.coerceIn(0.75f, 1.6f),
        cornerRadius = cornerRadius.coerceIn(0f, 48f),
        paddingDp = paddingDp.coerceIn(8f, 32f),
    )
}

internal fun OverlayConfig.effectiveBackground(): OverlayBackground =
    if (layout == OverlayLayout.CURRENT_WORD) {
        OverlayBackground.TRANSPARENT
    } else {
        background
    }

/**
 * The system overlay window itself must remain fully opaque at the compositor boundary.
 * Applying user opacity to WindowManager.LayoutParams.alpha makes some System UI
 * compositors treat the full window bounds as a translucent veil.
 */
internal fun OverlayConfig.systemWindowAlpha(): Float = 1f

internal fun OverlayConfig.renderedContentOpacity(): Float {
    val normalizedOpacity = opacity.coerceIn(0.25f, 1f)
    return if (clickThrough) {
        normalizedOpacity.coerceAtMost(MAX_CLICK_THROUGH_CONTENT_OPACITY)
    } else {
        normalizedOpacity
    }
}

private const val MAX_CLICK_THROUGH_CONTENT_OPACITY = 0.8f

internal fun migrateLegacyOverlayBackground(
    storedBackground: OverlayBackground?,
    migrationComplete: Boolean,
): OverlayBackground = when {
    !migrationComplete && (
        storedBackground == null || storedBackground == OverlayBackground.SUBTLE_DARK
    ) -> OverlayBackground.TRANSPARENT
    else -> storedBackground ?: OverlayBackground.TRANSPARENT
}

data class OverlayLyricsState(
    val trackTitle: String? = null,
    val artist: String? = null,
    val artworkUri: String? = null,
    val previousLine: String? = null,
    val currentLine: String? = null,
    val nextLine: String? = null,
    val words: List<String> = emptyList(),
    val activeWordIndex: Int? = null,
    val activeWordId: String? = null,
    val wordProgress: Float = 0f,
    val lineProgress: Float = 0f,
    val offsetMs: Long = 0L,
    val isPlaying: Boolean = false,
    val currentCue: LyricLineUi? = null,
    val previousCue: LyricLineUi? = null,
    val nextCue: LyricLineUi? = null,
    val activeCues: List<LyricLineUi> = emptyList(),
    val timeline: LyricsTimeline? = null,
    val motionLevel: MotionLevel = MotionLevel.NORMAL,
    val karaokeStyle: KaraokeVisualStyle = KaraokeVisualStyle(),
) {
    fun normalized(): OverlayLyricsState = copy(
        activeWordIndex = activeWordIndex?.takeIf { it in words.indices },
        wordProgress = wordProgress.coerceIn(0f, 1f),
        lineProgress = lineProgress.coerceIn(0f, 1f),
    )
}

sealed interface OverlayRuntimeStatus {
    data object Stopped : OverlayRuntimeStatus
    data object Starting : OverlayRuntimeStatus
    data object Visible : OverlayRuntimeStatus
    data object HiddenWhilePaused : OverlayRuntimeStatus
    data object HiddenOnLockScreen : OverlayRuntimeStatus
    data object PermissionRequired : OverlayRuntimeStatus
    data class Error(val reason: String) : OverlayRuntimeStatus
}

internal enum class OverlayVisibility {
    VISIBLE,
    MINIMAL_ON_LOCK_SCREEN,
    HIDDEN_WHILE_PAUSED,
    HIDDEN_ON_LOCK_SCREEN,
}

internal fun OverlayConfig.visibility(
    isPlaying: Boolean,
    isKeyguardLocked: Boolean,
): OverlayVisibility = when {
    isKeyguardLocked && lockScreenMode == LockScreenOverlayMode.OFF ->
        OverlayVisibility.HIDDEN_ON_LOCK_SCREEN

    hideWhenPaused && !isPlaying -> OverlayVisibility.HIDDEN_WHILE_PAUSED
    isKeyguardLocked && lockScreenMode == LockScreenOverlayMode.MINIMAL ->
        OverlayVisibility.MINIMAL_ON_LOCK_SCREEN

    else -> OverlayVisibility.VISIBLE
}

internal data class OverlayPosition(val x: Int, val y: Int)

internal fun clampOverlayPosition(
    x: Int,
    y: Int,
    alignment: OverlayAlignment,
    availableWidth: Int,
    availableHeight: Int,
    overlayWidth: Int,
    overlayHeight: Int,
    margin: Int,
): OverlayPosition {
    val safeMargin = margin.coerceAtLeast(0)
    val maxX = (availableWidth - overlayWidth - safeMargin).coerceAtLeast(safeMargin)
    val maxY = (availableHeight - overlayHeight - safeMargin).coerceAtLeast(safeMargin)
    return OverlayPosition(
        x = if (alignment == OverlayAlignment.CENTER) 0 else x.coerceIn(safeMargin, maxX),
        y = y.coerceIn(safeMargin, maxY),
    )
}
