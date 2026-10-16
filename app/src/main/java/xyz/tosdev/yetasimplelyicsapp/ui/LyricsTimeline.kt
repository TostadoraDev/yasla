package xyz.tosdev.yetasimplelyicsapp.ui

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** One playback sample for every visible surface. Read only in the drawing phase. */
@Stable
class LyricsTimeline(initialPositionMs: Long? = null) {
    var positionMs: Long? by mutableStateOf(initialPositionMs)
        internal set
}

enum class HighlightStyle { SOLID, WHITE_GRADIENT, ACCENT_GRADIENT, ARTWORK_GRADIENT, CUSTOM_GRADIENT }
enum class InactiveStyle { SOLID, OPACITY, CUSTOM }

@Immutable
data class KaraokeVisualStyle(
    val highlight: HighlightStyle = HighlightStyle.WHITE_GRADIENT,
    val inactive: InactiveStyle = InactiveStyle.OPACITY,
    val customStartArgb: Long = 0xFFFFFFFF,
    val customEndArgb: Long = 0xFFA8C7FA,
    val inactiveArgb: Long = 0xFF8D9096,
    val featherDp: Float = 10f,
    val inactiveOpacity: Float = 0.45f,
)
