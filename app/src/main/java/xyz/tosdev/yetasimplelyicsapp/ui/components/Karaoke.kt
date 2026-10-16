package xyz.tosdev.yetasimplelyicsapp.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import xyz.tosdev.yetasimplelyicsapp.domain.lyrics.LyricsAlignment
import xyz.tosdev.yetasimplelyicsapp.ui.HighlightStyle
import xyz.tosdev.yetasimplelyicsapp.ui.InactiveStyle
import xyz.tosdev.yetasimplelyicsapp.ui.KaraokeVisualStyle
import xyz.tosdev.yetasimplelyicsapp.ui.KaraokeWordUi
import xyz.tosdev.yetasimplelyicsapp.ui.LyricLineUi
import xyz.tosdev.yetasimplelyicsapp.ui.LyricsTimeline
import xyz.tosdev.yetasimplelyicsapp.ui.MotionLevel
import kotlin.math.abs
import kotlin.math.roundToInt

internal data class LyricsLinePresentation(
    val alpha: Float,
    val translationYDp: Float,
    val scale: Float,
)

/** Compatibility wrapper used by the editor preview. Production surfaces use [KaraokeText]. */
@Composable
fun KaraokeLine(
    line: LyricLineUi,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.headlineMedium,
    inactiveColor: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.26f),
    activeColor: Color = MaterialTheme.colorScheme.onSurface,
    alignment: TextAlign = TextAlign.Start,
    motionLevel: MotionLevel = MotionLevel.NORMAL,
) {
    val renderedLine = remember(line) { lyricsLineForDisplay(line) }
    KaraokeText(
        line = renderedLine,
        positionMs = { null },
        modifier = modifier.fillMaxWidth(),
        textStyle = style,
        visualStyle = KaraokeVisualStyle(
            highlight = HighlightStyle.CUSTOM_GRADIENT,
            inactive = InactiveStyle.CUSTOM,
            customStartArgb = activeColor.toArgb().toUInt().toLong(),
            customEndArgb = activeColor.toArgb().toUInt().toLong(),
            inactiveArgb = inactiveColor.toArgb().toUInt().toLong(),
            featherDp = 8f,
            inactiveOpacity = inactiveColor.alpha,
        ),
        alignment = alignment.toLyricsAlignment(),
        motionLevel = motionLevel,
        isActive = renderedLine.isActive,
    )
}

@Composable
fun AutoScrollingLyrics(
    lines: List<LyricLineUi>,
    activeLineId: String?,
    motionLevel: MotionLevel,
    modifier: Modifier = Modifier,
    showTranslation: Boolean = true,
    canSeek: Boolean = false,
    alignmentOverride: String = "AUTO",
    timeline: LyricsTimeline? = null,
    visualStyle: KaraokeVisualStyle = KaraokeVisualStyle(),
    artworkUri: String? = null,
    onSeekLine: (String) -> Unit = {},
    isPlaying: Boolean = true,
    lyricsTextScale: Float = 1f,
) {
    AmllLyricsViewport(
        lines = lines, activeLineId = activeLineId, motionLevel = motionLevel,
        modifier = modifier, showTranslation = showTranslation, canSeek = canSeek,
        alignmentOverride = alignmentOverride, timeline = timeline, visualStyle = visualStyle,
        artworkUri = artworkUri, onSeekLine = onSeekLine, isPlaying = isPlaying,
        lyricsTextScale = lyricsTextScale,
    )
}

internal fun lyricsLineForDisplay(line: LyricLineUi): LyricLineUi =
    if (line.isPast) line.copy(isActive = true, words = line.words.map { it.copy(progress = 1f) }) else line

internal fun lyricsRowAlignment(
    lines: List<LyricLineUi>,
    index: Int,
    override: String,
): LyricsAlignment {
    val line = lines.getOrNull(index) ?: return LyricsAlignment.LEFT
    if (!line.isBackground) return line.resolveAlignment(override)
    val primary = lines.withIndex()
        .asSequence()
        .filter { !it.value.isBackground }
        .minWithOrNull(
            compareBy<IndexedValue<LyricLineUi>>(
                { if (it.value.startTimeMs == line.startTimeMs) 0 else 1 },
                { abs(it.index - index) },
            ),
        )
        ?.value
    return (primary ?: line).resolveAlignment(override)
}

private fun LyricLineUi.resolveAlignment(override: String): LyricsAlignment {
    if (sourceAlignment != LyricsAlignment.AUTO) return alignment
    return when (override) {
        "CENTER" -> LyricsAlignment.CENTER
        "END", "RIGHT" -> LyricsAlignment.RIGHT
        "START", "LEFT" -> LyricsAlignment.LEFT
        else -> alignment
    }
}

private fun LyricsAlignment.horizontalAlignment(): Alignment.Horizontal = when (this) {
    LyricsAlignment.RIGHT -> Alignment.End
    LyricsAlignment.CENTER -> Alignment.CenterHorizontally
    else -> Alignment.Start
}

private fun LyricsAlignment.textAlign(): TextAlign = when (this) {
    LyricsAlignment.RIGHT -> TextAlign.End
    LyricsAlignment.CENTER -> TextAlign.Center
    else -> TextAlign.Start
}

private fun LyricsAlignment.transformOriginX(): Float = when (this) {
    LyricsAlignment.RIGHT -> 1f
    LyricsAlignment.CENTER -> 0.5f
    else -> 0f
}

private fun TextAlign.toLyricsAlignment(): LyricsAlignment = when (this) {
    TextAlign.Center -> LyricsAlignment.CENTER
    TextAlign.End, TextAlign.Right -> LyricsAlignment.RIGHT
    else -> LyricsAlignment.LEFT
}

internal data class KaraokeWordGroup(val syllables: List<KaraokeWordUi>)

internal fun groupSyllablesIntoWords(
    words: List<KaraokeWordUi>,
    originalText: String?,
): List<KaraokeWordGroup> {
    if (words.isEmpty()) return emptyList()
    val fullText = originalText?.takeIf(String::isNotBlank)
        ?: return words.map { KaraokeWordGroup(listOf(it)) }
    val groups = mutableListOf<KaraokeWordGroup>()
    val currentSyllables = mutableListOf<KaraokeWordUi>()
    var searchIdx = 0
    for (index in words.indices) {
        val word = words[index]
        val clean = word.text.trim()
        if (clean.isEmpty()) continue
        currentSyllables += word
        val matchIdx = fullText.indexOf(clean, searchIdx)
        if (matchIdx < 0) {
            groups += KaraokeWordGroup(currentSyllables.toList())
            currentSyllables.clear()
            continue
        }
        val endIdx = matchIdx + clean.length
        searchIdx = endIdx
        val nextClean = words.getOrNull(index + 1)?.text?.trim()
        val nextMatch =
            nextClean?.takeIf(String::isNotEmpty)?.let { fullText.indexOf(it, searchIdx) } ?: -1
        val wordEnds = endIdx >= fullText.length || fullText[endIdx].isWhitespace() ||
                (nextMatch > endIdx && fullText.substring(endIdx, nextMatch)
                    .any(Char::isWhitespace))
        if (wordEnds) {
            groups += KaraokeWordGroup(currentSyllables.toList())
            currentSyllables.clear()
        }
    }
    if (currentSyllables.isNotEmpty()) groups += KaraokeWordGroup(currentSyllables.toList())
    return groups
}

internal fun lyricFocusOffsetPx(
    viewportHeightPx: Int,
    currentItemHeightPx: Int = 0,
): Int {
    val viewport = viewportHeightPx.coerceAtLeast(0)
    val itemHeight = currentItemHeightPx.coerceAtLeast(0)
    val desiredCenterY = viewport * LYRICS_FOCUS_FRACTION
    return (desiredCenterY - (itemHeight / 2f))
        .roundToInt()
        .coerceIn(0, (viewport - itemHeight).coerceAtLeast(0))
}

internal fun isLargeLyricsJump(
    previousIndex: Int,
    currentIndex: Int,
    lyricsChanged: Boolean,
): Boolean = lyricsChanged || abs(currentIndex - previousIndex) > MAX_SMOOTH_LINE_JUMP

internal fun lyricsLinePresentation(
    index: Int,
    activeIndex: Int,
    isActive: Boolean,
    motionLevel: MotionLevel,
): LyricsLinePresentation {
    val distance = if (isActive) 0f else if (activeIndex < 0) 3f else abs(index - activeIndex).toFloat()
    return lyricsDistancePresentation(distance, motionLevel)
}

internal const val LYRICS_FOCUS_FRACTION = 0.27f
private const val MAX_SMOOTH_LINE_JUMP = 2
