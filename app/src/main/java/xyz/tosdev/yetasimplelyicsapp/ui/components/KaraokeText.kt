package xyz.tosdev.yetasimplelyicsapp.ui.components

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.style.ResolvedTextDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import xyz.tosdev.yetasimplelyicsapp.domain.lyrics.LyricsAlignment
import xyz.tosdev.yetasimplelyicsapp.ui.HighlightStyle
import xyz.tosdev.yetasimplelyicsapp.ui.InactiveStyle
import xyz.tosdev.yetasimplelyicsapp.ui.KaraokeVisualStyle
import xyz.tosdev.yetasimplelyicsapp.ui.LyricLineUi
import xyz.tosdev.yetasimplelyicsapp.ui.MotionLevel
import xyz.tosdev.yetasimplelyicsapp.ui.designsystem.SignalMotionScheme
import xyz.tosdev.yetasimplelyicsapp.ui.designsystem.SignalMotionSpecs
import xyz.tosdev.yetasimplelyicsapp.ui.designsystem.rememberSignalMotion
import xyz.tosdev.yetasimplelyicsapp.ui.theme.PaletteEngine
import xyz.tosdev.yetasimplelyicsapp.ui.theme.PaletteRequest
import xyz.tosdev.yetasimplelyicsapp.ui.theme.PaletteSource
import xyz.tosdev.yetasimplelyicsapp.ui.timedSegments
import java.io.File
import java.io.InputStream
import kotlin.math.max
import kotlin.math.min

@Immutable
data class KaraokeColors(
    val highlight: List<Color>,
    val inactive: Color,
)

internal data class TimedTextSegment(
    val wordIndex: Int,
    val startOffset: Int,
    val endOffset: Int,
    val startTimeMs: Long?,
    val endTimeMs: Long?,
    val fallbackProgress: Float,
    val visualWeight: Int,
)

internal data class TimedSegmentWindow(
    val startTimeMs: Long?,
    val endTimeMs: Long?,
    val visualWeight: Int,
)

internal data class SegmentedWordProgress(
    val overallProgress: Float,
    val completedSegments: Int,
    val activeSegmentIndex: Int,
    val activeSegmentProgress: Float,
    val usesSegments: Boolean,
) {
    fun progressForSegment(index: Int): Float = when {
        !usesSegments -> overallProgress
        index < completedSegments -> 1f
        index == activeSegmentIndex -> activeSegmentProgress
        else -> 0f
    }
}

internal data class SemanticWordEntrance(
    val sourceWordIndex: Int,
    val id: String,
    val startOffset: Int,
    val endOffset: Int,
    val startTimeMs: Long?,
    val endTimeMs: Long?,
    val fallbackProgress: Float,
)

internal data class WordPresentation(
    val opacity: Float,
    val translationYDp: Float,
    val scale: Float,
    val trackingScale: Float = 1f,
)

private data class WordDrawFragment(
    val wordIndex: Int,
    val bounds: Rect,
    val lineIndex: Int,
    val direction: ResolvedTextDirection,
)

private data class WordFragmentKey(
    val wordIndex: Int,
    val lineIndex: Int,
    val direction: ResolvedTextDirection,
)

private data class DrawFragment(
    val segmentIndex: Int,
    val bounds: Rect,
    val lineBounds: Rect,
    val lineIndex: Int,
    val direction: ResolvedTextDirection,
    val fractionStart: Float,
    val fractionEnd: Float,
)

private data class RawDrawFragment(
    val segmentIndex: Int,
    val bounds: Rect,
    val lineIndex: Int,
    val direction: ResolvedTextDirection,
)

internal data class TextOffsetRange(
    val start: Int,
    val endExclusive: Int,
)

internal data class KaraokeLayerAlphas(
    val baseText: Float,
    val activeForeground: Float,
)

internal data class RevealSlice(
    val bounds: Rect,
    val alpha: Float,
)

private val karaokePaletteEngine = PaletteEngine(maxCacheEntries = 16)

@Composable
fun rememberKaraokeColors(
    style: KaraokeVisualStyle,
    artworkUri: String?,
    motionLevel: MotionLevel,
): KaraokeColors {
    val motion = rememberSignalMotion(motionLevel)
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val artworkPalette by produceState<Pair<Color, Color>?>(
        initialValue = null,
        key1 = if (style.highlight == HighlightStyle.ARTWORK_GRADIENT) artworkUri else null,
    ) {
        value =
            if (style.highlight == HighlightStyle.ARTWORK_GRADIENT && !artworkUri.isNullOrBlank()) {
                val bitmap =
                    withContext(Dispatchers.IO) { decodePaletteArtwork(context, artworkUri) }
                bitmap?.let {
                    val palette = karaokePaletteEngine.resolve(
                        context = context,
                        request = PaletteRequest(
                            source = PaletteSource.ARTWORK,
                            dark = scheme.background.luminance() < 0.5f,
                        ),
                        artwork = it,
                    )
                    palette.primary to palette.secondary
                }
            } else {
                null
            }
    }

    val rawHighlight = when (style.highlight) {
        HighlightStyle.SOLID -> scheme.onSurface to scheme.onSurface
        HighlightStyle.WHITE_GRADIENT -> whiteKaraokeGradient()
        HighlightStyle.ACCENT_GRADIENT -> scheme.primary to scheme.tertiary
        HighlightStyle.ARTWORK_GRADIENT -> artworkPalette ?: (scheme.primary to scheme.secondary)
        HighlightStyle.CUSTOM_GRADIENT -> Color(style.customStartArgb) to Color(style.customEndArgb)
    }
    val start by animateColorAsState(
        rawHighlight.first,
        SignalMotionSpecs.emphasized(motion),
        label = "karaoke palette start"
    )
    val end by animateColorAsState(
        rawHighlight.second,
        SignalMotionSpecs.emphasized(motion),
        label = "karaoke palette end"
    )
    val inactive = when (style.inactive) {
        InactiveStyle.SOLID -> scheme.onSurfaceVariant
        InactiveStyle.OPACITY -> scheme.onSurface.copy(
            alpha = style.inactiveOpacity.coerceIn(
                0.12f,
                1f
            )
        )

        InactiveStyle.CUSTOM -> Color(style.inactiveArgb)
    }
    return remember(start, end, inactive) { KaraokeColors(listOf(start, end), inactive) }
}

internal fun whiteKaraokeGradient(): Pair<Color, Color> =
    Color.White to Color(0xFFE8EEF9)

internal fun KaraokeColors.forTransparentOverlay(style: KaraokeVisualStyle): KaraokeColors {
    val overlayHighlight = when (style.highlight) {
        HighlightStyle.SOLID -> listOf(Color.White, Color.White)
        HighlightStyle.WHITE_GRADIENT -> whiteKaraokeGradient().toList()
        else -> highlight
    }
    val overlayInactive = when (style.inactive) {
        InactiveStyle.SOLID -> Color.White.copy(alpha = 0.62f)
        InactiveStyle.OPACITY -> Color.White.copy(
            alpha = style.inactiveOpacity.coerceIn(0.12f, 1f),
        )
        InactiveStyle.CUSTOM -> inactive
    }
    return KaraokeColors(
        highlight = overlayHighlight,
        inactive = overlayInactive,
    )
}

@Composable
fun KaraokeText(
    line: LyricLineUi,
    positionMs: () -> Long?,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = MaterialTheme.typography.headlineLarge,
    visualStyle: KaraokeVisualStyle = KaraokeVisualStyle(),
    alignment: LyricsAlignment = line.alignment,
    motionLevel: MotionLevel = MotionLevel.NORMAL,
    artworkUri: String? = null,
    isActive: Boolean = line.isActive,
    activeForegroundAlpha: Float = 1f,
    inactiveTextAlpha: Float = 1f,
    colors: KaraokeColors? = null,
    refinedMotion: Boolean = false,
    wordMotionAmount: () -> Float = { 1f },
) {
    val motion = rememberSignalMotion(motionLevel)
    val resolvedColors = colors ?: rememberKaraokeColors(visualStyle, artworkUri, motionLevel)
    val text = line.plainText
    val textAlign = alignment.asTextAlign()
    val layoutDirection = LocalLayoutDirection.current
    val textDirection = when (line.direction) {
        xyz.tosdev.yetasimplelyicsapp.domain.lyrics.LyricsTextDirection.LTR -> TextDirection.Ltr
        xyz.tosdev.yetasimplelyicsapp.domain.lyrics.LyricsTextDirection.RTL -> TextDirection.Rtl
        xyz.tosdev.yetasimplelyicsapp.domain.lyrics.LyricsTextDirection.AUTO ->
            if (layoutDirection == LayoutDirection.Rtl) {
                TextDirection.ContentOrRtl
            } else {
                TextDirection.ContentOrLtr
            }
    }
    val segments =
        remember(line.words, line.originalText, line.startTimeMs, line.endTimeMs, isActive) {
            resolveTimedTextSegments(line, isActive)
        }
    val semanticWords =
        remember(line.words, line.originalText, isActive) {
            resolveSemanticWordEntrances(line, isActive)
        }
    val hasProgressiveTiming = segments.isNotEmpty()
    val layerAlphas = resolveKaraokeLayerAlphas(
        isActive = isActive,
        hasProgressiveTiming = hasProgressiveTiming,
        inactiveTextAlpha = inactiveTextAlpha,
        activeForegroundAlpha = activeForegroundAlpha,
    )
    val baseTargetColor = if (isActive && !hasProgressiveTiming) {
        resolvedColors.highlight.first()
    } else {
        resolvedColors.inactive
    }
    val baseColor by animateColorAsState(
        targetValue = baseTargetColor,
        animationSpec = SignalMotionSpecs.standard(motion),
        label = "uniform lyric color",
    )
    val baseAlpha by animateFloatAsState(
        targetValue = layerAlphas.baseText,
        animationSpec = SignalMotionSpecs.standard(motion),
        label = "uniform lyric opacity",
    )
    var textLayout by remember(text, textStyle, textAlign, layoutDirection) {
        mutableStateOf<TextLayoutResult?>(null)
    }
    val fragments = remember(textLayout, segments) {
        textLayout?.let { buildDrawFragments(it, segments) }.orEmpty()
    }
    val wordFragments = remember(textLayout, semanticWords) {
        textLayout?.let { buildWordDrawFragments(it, semanticWords) }.orEmpty()
    }
    val semanticIndexBySource = remember(semanticWords) {
        semanticWords.mapIndexed { index, word -> word.sourceWordIndex to index }.toMap()
    }
    val semanticWordBySource = remember(semanticWords, line.words.size) {
        Array<SemanticWordEntrance?>(line.words.size) { sourceIndex ->
            semanticWords.firstOrNull { it.sourceWordIndex == sourceIndex }
        }
    }
    val segmentLocalIndex = remember(segments, line.words.size) {
        val counts = IntArray(line.words.size)
        IntArray(segments.size) { segmentIndex ->
            val sourceIndex = segments[segmentIndex].wordIndex
            counts[sourceIndex].also { counts[sourceIndex]++ }
        }
    }
    val segmentWindowsByWord = remember(segments, line.words) {
        Array(line.words.size) { sourceIndex ->
            if (line.words[sourceIndex].segments.isEmpty()) {
                emptyList()
            } else {
                segments.filter { it.wordIndex == sourceIndex }.map { segment ->
                    TimedSegmentWindow(
                        startTimeMs = segment.startTimeMs,
                        endTimeMs = segment.endTimeMs,
                        visualWeight = segment.visualWeight,
                    )
                }
            }
        }
    }
    val wordBoundsByKey = remember(wordFragments) {
        wordFragments.associate { fragment ->
            WordFragmentKey(fragment.wordIndex, fragment.lineIndex, fragment.direction) to
                fragment.bounds
        }
    }
    val expectedWordCount = remember(line.words) { line.words.count { it.text.isNotBlank() } }
    val wordAnimationAvailable = semanticWords.size == expectedWordCount && expectedWordCount > 0

    Box(
        modifier = modifier.semantics(mergeDescendants = true) {
            contentDescription = buildString {
                append(text)
                line.translation?.takeIf(String::isNotBlank)?.let {
                    append(". ")
                    append(it)
                }
            }
        },
    ) {
        BasicText(
            text = text,
            modifier = Modifier.fillMaxWidth(),
            style = textStyle.copy(
                color = baseColor.copy(
                    alpha = if (hasProgressiveTiming) 0f else baseColor.alpha * baseAlpha,
                ),
                textAlign = textAlign,
                textDirection = textDirection,
            ),
        )
        if (hasProgressiveTiming) {
            BasicText(
                text = text,
                onTextLayout = { textLayout = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .drawWithCache {
                        val brushes = fragments.map { fragment ->
                            fragmentBrush(fragment, resolvedColors.highlight)
                        }
                        onDrawWithContent {
                            val layout = textLayout ?: return@onDrawWithContent
                            val now = positionMs()
                            val segmentedProgressBySource = Array(line.words.size) { sourceIndex ->
                                val semanticWord = semanticWordBySource[sourceIndex]
                                segmentedWordProgressAt(
                                    positionMs = now,
                                    wordStartTimeMs = semanticWord?.startTimeMs,
                                    wordEndTimeMs = semanticWord?.endTimeMs,
                                    fallbackProgress = semanticWord?.fallbackProgress ?: 0f,
                                    segments = segmentWindowsByWord[sourceIndex],
                                    smoothBoundaries = refinedMotion,
                                )
                            }
                            val wordPresentations = semanticWords.map { word ->
                                if (refinedMotion) refinedWordPresentationAt(
                                    progress = karaokeProgressAt(now, word.startTimeMs, word.endTimeMs, word.fallbackProgress),
                                    level = if (motion.enabled) motionLevel else MotionLevel.OFF,
                                    motionAmount = wordMotionAmount(),
                                    durationMs = word.startTimeMs?.let { start ->
                                        word.endTimeMs?.minus(start)
                                    },
                                ) else wordPresentationAt(
                                    wordProgress = segmentedProgressBySource[word.sourceWordIndex]
                                        .overallProgress,
                                    motionLevel = motionLevel,
                                )
                            }
                            if (wordAnimationAvailable && wordFragments.isNotEmpty()) {
                                wordFragments.forEach { fragment ->
                                    drawWordBase(
                                        layout = layout,
                                        fragment = fragment,
                                        presentation = wordPresentations
                                            .getOrElse(fragment.wordIndex) { WordPresentation(1f, 0f, 1f) },
                                        color = baseColor,
                                        alpha = baseAlpha,
                                    )
                                }
                            } else {
                                drawText(layout, color = baseColor, alpha = baseAlpha)
                            }
                            fragments.forEachIndexed { index, fragment ->
                                val segment = segments[fragment.segmentIndex]
                                val segmentedProgress = segmentedProgressBySource[segment.wordIndex]
                                val segmentProgress = if (segmentedProgress.usesSegments) {
                                    segmentedProgress.progressForSegment(
                                        segmentLocalIndex[fragment.segmentIndex],
                                    )
                                } else {
                                    karaokeProgressAt(
                                        positionMs = now,
                                        startTimeMs = segment.startTimeMs,
                                        endTimeMs = segment.endTimeMs,
                                        fallbackProgress = segment.fallbackProgress,
                                    )
                                }
                                val span =
                                    (fragment.fractionEnd - fragment.fractionStart).coerceAtLeast(
                                        0.0001f
                                    )
                                val progress =
                                    ((segmentProgress - fragment.fractionStart) / span).coerceIn(
                                        0f,
                                        1f
                                    )
                                val semanticIndex = semanticIndexBySource[segment.wordIndex]
                                drawFragment(
                                    layout = layout,
                                    fragment = fragment,
                                    progress = progress,
                                    foregroundAlpha = layerAlphas.activeForeground,
                                    brush = brushes[index],
                                    presentation = if (wordAnimationAvailable) {
                                        semanticIndex?.let(wordPresentations::getOrNull)
                                    } else {
                                        null
                                    },
                                    pivot = semanticIndex?.let { index ->
                                        wordBoundsByKey[
                                            WordFragmentKey(
                                                wordIndex = index,
                                                lineIndex = fragment.lineIndex,
                                                direction = fragment.direction,
                                            )
                                        ]?.center
                                    },
                                    featherPx = if (refinedMotion) {
                                        visualStyle.featherDp.coerceIn(2f, 4f).dp.toPx()
                                    } else {
                                        0f
                                    },
                                )
                            }
                        }
                    }
                    .clearAndSetSemantics { },
                style = textStyle.copy(
                    brush = null,
                    alpha = 0f,
                    background = Color.Unspecified,
                    textAlign = textAlign,
                    textDirection = textDirection,
                ),
            )
        }
    }
}

internal fun karaokeProgressAt(
    positionMs: Long?,
    startTimeMs: Long?,
    endTimeMs: Long?,
    fallbackProgress: Float,
): Float {
    if (positionMs == null || startTimeMs == null || endTimeMs == null || endTimeMs <= startTimeMs) {
        return fallbackProgress.coerceIn(0f, 1f)
    }
    return ((positionMs - startTimeMs).toFloat() / (endTimeMs - startTimeMs).toFloat()).coerceIn(
        0f,
        1f
    )
}

internal fun segmentedWordProgressAt(
    positionMs: Long?,
    wordStartTimeMs: Long?,
    wordEndTimeMs: Long?,
    fallbackProgress: Float,
    segments: List<TimedSegmentWindow>,
    smoothBoundaries: Boolean = false,
): SegmentedWordProgress {
    val isValid: (TimedSegmentWindow) -> Boolean = { segment ->
        val start = segment.startTimeMs
        val end = segment.endTimeMs
        start != null && end != null && end > start && segment.visualWeight > 0
    }
    val validSegments = if (segments.all(isValid)) segments else segments.filter(isValid)
    if (validSegments.isEmpty()) {
        return SegmentedWordProgress(
            overallProgress = karaokeProgressAt(
                positionMs = positionMs,
                startTimeMs = wordStartTimeMs,
                endTimeMs = wordEndTimeMs,
                fallbackProgress = fallbackProgress,
            ),
            completedSegments = 0,
            activeSegmentIndex = -1,
            activeSegmentProgress = 0f,
            usesSegments = false,
        )
    }

    val totalWeight = validSegments.sumOf { it.visualWeight }.coerceAtLeast(1)
    val fallback = fallbackProgress.coerceIn(0f, 1f)
    if (positionMs == null) {
        return segmentedProgressFromOverall(fallback, validSegments, totalWeight)
    }

    val activeIndex = validSegments.indexOfFirst { segment ->
        val start = segment.startTimeMs ?: return@indexOfFirst false
        val end = segment.endTimeMs ?: return@indexOfFirst false
        positionMs in start until end
    }
    val completedSegments = validSegments.count { segment ->
        (segment.endTimeMs ?: Long.MAX_VALUE) <= positionMs
    }
    if (activeIndex < 0) {
        val completedWeight = validSegments.take(completedSegments).sumOf { it.visualWeight }
        return SegmentedWordProgress(
            overallProgress = completedWeight.toFloat() / totalWeight,
            completedSegments = completedSegments,
            activeSegmentIndex = -1,
            activeSegmentProgress = 0f,
            usesSegments = true,
        )
    }

    val active = validSegments[activeIndex]
    val linearProgress = karaokeProgressAt(
        positionMs = positionMs,
        startTimeMs = active.startTimeMs,
        endTimeMs = active.endTimeMs,
        fallbackProgress = fallback,
    )
    val activeProgress = if (smoothBoundaries) {
        smoothSegmentProgress(validSegments, activeIndex, linearProgress)
    } else linearProgress
    val completedWeight = validSegments.take(activeIndex).sumOf { it.visualWeight }
    return SegmentedWordProgress(
        overallProgress = (
            completedWeight + (active.visualWeight * activeProgress)
            ) / totalWeight.toFloat(),
        completedSegments = activeIndex,
        activeSegmentIndex = activeIndex,
        activeSegmentProgress = activeProgress,
        usesSegments = true,
    )
}

private fun segmentedProgressFromOverall(
    progress: Float,
    segments: List<TimedSegmentWindow>,
    totalWeight: Int,
): SegmentedWordProgress {
    val targetWeight = progress.coerceIn(0f, 1f) * totalWeight
    var consumedWeight = 0
    segments.forEachIndexed { index, segment ->
        val nextWeight = consumedWeight + segment.visualWeight
        if (targetWeight < nextWeight || index == segments.lastIndex) {
            val localProgress = (
                (targetWeight - consumedWeight) / segment.visualWeight.coerceAtLeast(1)
                ).coerceIn(0f, 1f)
            return SegmentedWordProgress(
                overallProgress = progress.coerceIn(0f, 1f),
                completedSegments = index,
                activeSegmentIndex = index,
                activeSegmentProgress = localProgress,
                usesSegments = true,
            )
        }
        consumedWeight = nextWeight
    }
    return SegmentedWordProgress(1f, segments.size, -1, 0f, usesSegments = true)
}

internal fun wordPresentationAt(
    wordProgress: Float,
    motionLevel: MotionLevel,
): WordPresentation {
    val progress = wordProgress.coerceIn(0f, 1f)
    val arrival = smootherStep((progress / WORD_FOCUS_POINT).coerceIn(0f, 1f))
    val release = smootherStep(
        ((progress - WORD_FOCUS_POINT) / (1f - WORD_FOCUS_POINT)).coerceIn(0f, 1f),
    )
    val opacity = 0.94f + (0.06f * arrival)
    return when (motionLevel) {
        MotionLevel.OFF -> WordPresentation(
            opacity = if (progress > 0f) 1f else 0.94f,
            translationYDp = 0f,
            scale = 1f,
        )
        MotionLevel.REDUCED -> WordPresentation(opacity = opacity, translationYDp = 0f, scale = 1f)
        MotionLevel.NORMAL -> WordPresentation(
            opacity = opacity,
            translationYDp = (1.1f * (1f - arrival)) - (0.35f * release),
            scale = 0.996f + (0.004f * arrival),
        )
        MotionLevel.ENHANCED -> WordPresentation(
            opacity = opacity,
            translationYDp = (1.4f * (1f - arrival)) - (0.45f * release),
            scale = 0.994f + (0.006f * arrival),
        )
    }
}

private fun smootherStep(value: Float): Float {
    val progress = value.coerceIn(0f, 1f)
    return progress * progress * progress *
        (progress * ((progress * 6f) - 15f) + 10f)
}

private const val WORD_FOCUS_POINT = 0.55f

internal fun resolveSemanticWordEntrances(
    line: LyricLineUi,
    isActive: Boolean = line.isActive,
): List<SemanticWordEntrance> {
    if (!isActive) return emptyList()
    val text = line.plainText
    val mappedWords = mapWordRanges(text, line.words.map { it.text })
    val entrances = line.words.mapIndexedNotNull { index, word ->
        val range = mappedWords.getOrNull(index) ?: return@mapIndexedNotNull null
        val start = word.startTimeMs ?: word.timedSegments.mapNotNull { it.startTimeMs }.minOrNull()
        val end = word.endTimeMs ?: word.timedSegments.mapNotNull { it.endTimeMs }.maxOrNull()
        SemanticWordEntrance(
            sourceWordIndex = index,
            id = word.id,
            startOffset = range.first,
            endOffset = range.last + 1,
            startTimeMs = start,
            endTimeMs = end,
            fallbackProgress = word.progress,
        )
    }
    return entrances.mapIndexed { index, word ->
        word.copy(
            startOffset = if (index == 0) 0 else word.startOffset,
            endOffset = entrances.getOrNull(index + 1)?.startOffset ?: text.length,
        )
    }
}

internal fun resolveTimedTextSegments(
    line: LyricLineUi,
    isActive: Boolean = line.isActive,
): List<TimedTextSegment> {
    if (!isActive) return emptyList()
    val text = line.plainText
    val timedTokens = line.words.flatMapIndexed { wordIndex, word ->
        word.timedSegments.map { segment -> Triple(segment, word.progress, wordIndex) }
    }
    val mappedWords = mapWordRanges(text, timedTokens.map { it.first.text })
    val timedWords = timedTokens.mapIndexedNotNull { index, (segment, fallbackProgress, wordIndex) ->
        val range = mappedWords.getOrNull(index) ?: return@mapIndexedNotNull null
        val start = segment.startTimeMs
        val end = segment.endTimeMs
        if (start == null || end == null || end <= start) {
            return@mapIndexedNotNull null
        }
        TimedTextSegment(
            wordIndex = wordIndex,
            startOffset = range.first,
            endOffset = range.last + 1,
            startTimeMs = start,
            endTimeMs = end,
            fallbackProgress = fallbackProgress,
            visualWeight = (range.last + 1 - range.first).coerceAtLeast(1),
        )
    }
    return timedWords.mapIndexed { index, segment ->
        segment.copy(
            startOffset = if (index == 0) 0 else segment.startOffset,
            endOffset = timedWords.getOrNull(index + 1)?.startOffset ?: text.length,
        )
    }
}

internal fun resolveKaraokeLayerAlphas(
    isActive: Boolean,
    hasProgressiveTiming: Boolean,
    inactiveTextAlpha: Float,
    activeForegroundAlpha: Float,
): KaraokeLayerAlphas = when {
    isActive && hasProgressiveTiming -> KaraokeLayerAlphas(
        baseText = inactiveTextAlpha.coerceIn(0f, 1f),
        activeForeground = activeForegroundAlpha.coerceIn(0f, 1f),
    )

    isActive -> KaraokeLayerAlphas(
        baseText = activeForegroundAlpha.coerceIn(0f, 1f),
        activeForeground = 0f,
    )

    else -> KaraokeLayerAlphas(
        baseText = inactiveTextAlpha.coerceIn(0f, 1f),
        activeForeground = 0f,
    )
}

private fun mapWordRanges(text: String, words: List<String>): List<IntRange?> {
    var cursor = 0
    return words.map { rawWord ->
        val word = rawWord.trim()
        if (word.isEmpty()) return@map null
        val index = text.indexOf(word, startIndex = cursor)
        if (index < 0) {
            null
        } else {
            cursor = index + word.length
            index until cursor
        }
    }
}

private fun buildDrawFragments(
    layout: TextLayoutResult,
    segments: List<TimedTextSegment>,
): List<DrawFragment> {
    val lineBounds = (0 until layout.lineCount).associateWith { lineIndex ->
        layout.visualLineBounds(lineIndex)
    }
    return buildList {
        segments.forEachIndexed { segmentIndex, segment ->
            if (segment.startOffset >= segment.endOffset || segment.startOffset !in 0 until layout.layoutInput.text.length) {
                return@forEachIndexed
            }
            val safeEnd = segment.endOffset.coerceAtMost(layout.layoutInput.text.length)
            val firstLine = layout.getLineForOffset(segment.startOffset)
            val lastLine = layout.getLineForOffset((safeEnd - 1).coerceAtLeast(segment.startOffset))
            val visualLines = (firstLine..lastLine).map { lineIndex ->
                TextOffsetRange(
                    start = layout.getLineStart(lineIndex),
                    endExclusive = layout.getLineEnd(lineIndex, visibleEnd = false),
                )
            }
            val raw = splitTextRangeAcrossVisualLines(
                startOffset = segment.startOffset,
                endOffset = safeEnd,
                visualLines = visualLines,
            ).flatMap { range ->
                layout.splitByBidiRun(range).mapNotNull { (run, direction) ->
                    val bounds = layout.rangeBounds(run.start, run.endExclusive)
                        ?: return@mapNotNull null
                    RawDrawFragment(
                        segmentIndex = segmentIndex,
                        bounds = bounds,
                        lineIndex = layout.getLineForOffset(run.start),
                        direction = direction,
                    )
                }
            }
            val totalWidth = raw.sumOf { it.bounds.width.toDouble() }.toFloat().coerceAtLeast(1f)
            var consumed = 0f
            raw.forEach { fragment ->
                val startFraction = consumed / totalWidth
                consumed += fragment.bounds.width
                add(
                    DrawFragment(
                        segmentIndex = fragment.segmentIndex,
                        bounds = fragment.bounds,
                        lineBounds = lineBounds[fragment.lineIndex]
                            ?.takeUnless { it == Rect.Zero }
                            ?: fragment.bounds,
                        lineIndex = fragment.lineIndex,
                        direction = fragment.direction,
                        fractionStart = startFraction,
                        fractionEnd = consumed / totalWidth,
                    ),
                )
            }
        }
    }
}

private fun buildWordDrawFragments(
    layout: TextLayoutResult,
    words: List<SemanticWordEntrance>,
): List<WordDrawFragment> = buildList {
    words.forEachIndexed { wordIndex, word ->
        if (word.startOffset >= word.endOffset || word.startOffset !in 0 until layout.layoutInput.text.length) {
            return@forEachIndexed
        }
        val safeEnd = word.endOffset.coerceAtMost(layout.layoutInput.text.length)
        val firstLine = layout.getLineForOffset(word.startOffset)
        val lastLine = layout.getLineForOffset((safeEnd - 1).coerceAtLeast(word.startOffset))
        val visualLines = (firstLine..lastLine).map { lineIndex ->
            TextOffsetRange(
                start = layout.getLineStart(lineIndex),
                endExclusive = layout.getLineEnd(lineIndex, visibleEnd = false),
            )
        }
        splitTextRangeAcrossVisualLines(
            startOffset = word.startOffset,
            endOffset = safeEnd,
            visualLines = visualLines,
        ).forEach { range ->
            layout.splitByBidiRun(range).forEach { (run, direction) ->
                layout.rangeBounds(run.start, run.endExclusive)?.let { bounds ->
                    add(
                        WordDrawFragment(
                            wordIndex = wordIndex,
                            bounds = bounds,
                            lineIndex = layout.getLineForOffset(run.start),
                            direction = direction,
                        ),
                    )
                }
            }
        }
    }
}

private fun DrawScope.drawWordBase(
    layout: TextLayoutResult,
    fragment: WordDrawFragment,
    presentation: WordPresentation,
    color: Color,
    alpha: Float,
) {
    val translationPx = presentation.translationYDp.dp.toPx()
    withTransform({
        translate(0f, translationPx)
        scale(
            presentation.scale * presentation.trackingScale,
            presentation.scale,
            pivot = fragment.bounds.center,
        )
    }) {
        clipRect(
            left = fragment.bounds.left,
            top = fragment.bounds.top - 4.dp.toPx(),
            right = fragment.bounds.right,
            bottom = fragment.bounds.bottom + 4.dp.toPx(),
        ) {
            drawText(
                textLayoutResult = layout,
                color = color,
                alpha = (alpha * presentation.opacity).coerceIn(0f, 1f),
            )
        }
    }
}

private fun DrawScope.drawFragment(
    layout: TextLayoutResult,
    fragment: DrawFragment,
    progress: Float,
    foregroundAlpha: Float,
    brush: Brush,
    presentation: WordPresentation?,
    pivot: Offset?,
    featherPx: Float,
) {
    if (progress <= 0f || foregroundAlpha <= 0f) return
    val wordPresentation = presentation ?: WordPresentation(1f, 0f, 1f)
    val translationPx = wordPresentation.translationYDp * density
    withTransform({
        translate(0f, translationPx)
        scale(
            wordPresentation.scale * wordPresentation.trackingScale,
            wordPresentation.scale,
            pivot = pivot ?: fragment.bounds.center,
        )
    }) {
        val slices = softRevealSlices(
            textBounds = fragment.bounds,
            progress = progress,
            isRtl = fragment.direction == ResolvedTextDirection.Rtl,
            featherPx = featherPx,
        )
        slices.forEach { slice ->
            if (slice.bounds.width <= 0f || slice.bounds.height <= 0f || slice.alpha <= 0f) {
                return@forEach
            }
            clipRect(
                slice.bounds.left,
                slice.bounds.top - 4.dp.toPx(),
                slice.bounds.right,
                slice.bounds.bottom + 4.dp.toPx(),
            ) {
                drawText(
                    layout,
                    brush = brush,
                    alpha = foregroundAlpha * wordPresentation.opacity * slice.alpha,
                )
            }
        }
    }
}

private fun fragmentBrush(fragment: DrawFragment, colors: List<Color>): Brush {
    val bounds = fragment.lineBounds
    val isRtl = fragment.direction == ResolvedTextDirection.Rtl
    val centerY = bounds.center.y
    return Brush.linearGradient(
        colors = colors,
        start = Offset(if (isRtl) bounds.right else bounds.left, centerY),
        end = Offset(if (isRtl) bounds.left else bounds.right, centerY),
    )
}

internal fun splitTextRangeAcrossVisualLines(
    startOffset: Int,
    endOffset: Int,
    visualLines: List<TextOffsetRange>,
): List<TextOffsetRange> = visualLines.mapNotNull { line ->
    val start = max(startOffset, line.start)
    val end = minOf(endOffset, line.endExclusive)
    if (start < end) TextOffsetRange(start, end) else null
}

private fun TextLayoutResult.splitByBidiRun(
    range: TextOffsetRange,
): List<Pair<TextOffsetRange, ResolvedTextDirection>> = buildList {
    if (range.start >= range.endExclusive) return@buildList
    var runStart = range.start
    var direction = getBidiRunDirection(runStart)
    for (offset in (runStart + 1) until range.endExclusive) {
        val nextDirection = getBidiRunDirection(offset)
        if (nextDirection != direction) {
            add(TextOffsetRange(runStart, offset) to direction)
            runStart = offset
            direction = nextDirection
        }
    }
    add(TextOffsetRange(runStart, range.endExclusive) to direction)
}

private fun TextLayoutResult.rangeBounds(startOffset: Int, endOffset: Int): Rect? {
    val text = layoutInput.text
    val bounds = (startOffset until endOffset).mapNotNull { offset ->
        if (offset !in 0 until text.length || text[offset].isWhitespace()) {
            return@mapNotNull null
        }
        getBoundingBox(offset).takeIf { it.width > 0f && it.height > 0f }
    }
    return unionGlyphBounds(bounds).takeUnless { it == Rect.Zero }
}

private fun TextLayoutResult.visualLineBounds(lineIndex: Int): Rect {
    val start = getLineStart(lineIndex)
    val end = getLineEnd(lineIndex, visibleEnd = true)
    if (start >= end) return Rect.Zero
    return rangeBounds(start, end) ?: Rect(
        left = getLineLeft(lineIndex),
        top = getLineTop(lineIndex),
        right = getLineRight(lineIndex),
        bottom = getLineBottom(lineIndex),
    )
}

internal fun revealBounds(textBounds: Rect, progress: Float, isRtl: Boolean): Rect {
    val safeProgress = progress.coerceIn(0f, 1f)
    val boundary = if (isRtl) {
        textBounds.right - textBounds.width * safeProgress
    } else {
        textBounds.left + textBounds.width * safeProgress
    }
    return if (isRtl) {
        Rect(boundary, textBounds.top, textBounds.right, textBounds.bottom)
    } else {
        Rect(textBounds.left, textBounds.top, boundary, textBounds.bottom)
    }
}

/**
 * Produces a tiny trailing transition strictly inside real glyph bounds. Completed progress is a
 * single fully opaque slice, so no feather or residual inactive pixels survive at 1f.
 */
internal fun softRevealSlices(
    textBounds: Rect,
    progress: Float,
    isRtl: Boolean,
    featherPx: Float,
): List<RevealSlice> {
    val safeProgress = progress.coerceIn(0f, 1f)
    if (safeProgress <= 0f || textBounds.width <= 0f || textBounds.height <= 0f) return emptyList()
    if (safeProgress >= 1f) return listOf(RevealSlice(textBounds, 1f))
    val revealed = revealBounds(textBounds, safeProgress, isRtl)
    if (featherPx <= 0f) return listOf(RevealSlice(revealed, 1f))

    // Fade the feather itself at both endpoints. This prevents an early glow and guarantees that
    // the final few percent converge to a completely filled word instead of permanent gray.
    val endpointFactor = minOf(
        (safeProgress / 0.04f).coerceIn(0f, 1f),
        ((1f - safeProgress) / 0.04f).coerceIn(0f, 1f),
    )
    val actualFeather = minOf(
        featherPx,
        revealed.width,
        textBounds.width * 0.09f,
    ) * endpointFactor
    if (actualFeather < 0.25f) return listOf(RevealSlice(revealed, 1f))

    val slices = ArrayList<RevealSlice>(REVEAL_FEATHER_STEPS + 1)
    if (isRtl) {
        val fullStart = revealed.left + actualFeather
        if (fullStart < revealed.right) {
            slices += RevealSlice(Rect(fullStart, revealed.top, revealed.right, revealed.bottom), 1f)
        }
        repeat(REVEAL_FEATHER_STEPS) { step ->
            val start = revealed.left + actualFeather * step / REVEAL_FEATHER_STEPS
            val end = revealed.left + actualFeather * (step + 1) / REVEAL_FEATHER_STEPS
            val alpha = smootherStep((step + 0.5f) / REVEAL_FEATHER_STEPS)
            slices += RevealSlice(Rect(start, revealed.top, end, revealed.bottom), alpha)
        }
    } else {
        val transitionStart = revealed.right - actualFeather
        if (revealed.left < transitionStart) {
            slices += RevealSlice(Rect(revealed.left, revealed.top, transitionStart, revealed.bottom), 1f)
        }
        repeat(REVEAL_FEATHER_STEPS) { step ->
            val start = transitionStart + actualFeather * step / REVEAL_FEATHER_STEPS
            val end = transitionStart + actualFeather * (step + 1) / REVEAL_FEATHER_STEPS
            val alpha = 1f - smootherStep((step + 0.5f) / REVEAL_FEATHER_STEPS)
            slices += RevealSlice(Rect(start, revealed.top, end, revealed.bottom), alpha)
        }
    }
    return slices
}

internal fun unionGlyphBounds(glyphBounds: List<Rect>): Rect {
    if (glyphBounds.isEmpty()) return Rect.Zero
    return glyphBounds.drop(1).fold(glyphBounds.first()) { bounds, fragmentBounds ->
        Rect(
            left = minOf(bounds.left, fragmentBounds.left),
            top = minOf(bounds.top, fragmentBounds.top),
            right = maxOf(bounds.right, fragmentBounds.right),
            bottom = maxOf(bounds.bottom, fragmentBounds.bottom),
        )
    }
}

internal fun LyricsAlignment.asTextAlign(): TextAlign = when (this) {
    LyricsAlignment.LEFT -> TextAlign.Left
    LyricsAlignment.RIGHT -> TextAlign.Right
    LyricsAlignment.CENTER -> TextAlign.Center
    LyricsAlignment.AUTO -> TextAlign.Start
}

private const val REVEAL_FEATHER_STEPS = 5

private suspend fun decodePaletteArtwork(
    context: Context,
    rawUri: String
): android.graphics.Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    runCatching {
        openArtwork(context, rawUri)?.use {
            BitmapFactory.decodeStream(
                it,
                null,
                bounds
            )
        }
    }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sampleSize = 1
    while (maxOf(
            bounds.outWidth,
            bounds.outHeight
        ) / sampleSize > PALETTE_ARTWORK_EDGE_PX
    ) sampleSize *= 2
    val options = BitmapFactory.Options().apply {
        inSampleSize = sampleSize
        inPreferredConfig = android.graphics.Bitmap.Config.ARGB_8888
    }
    return runCatching {
        openArtwork(context, rawUri)?.use {
            BitmapFactory.decodeStream(
                it,
                null,
                options
            )
        }
    }.getOrNull()
}

private fun openArtwork(context: Context, rawUri: String): InputStream? {
    val uri = Uri.parse(rawUri)
    return when (uri.scheme?.lowercase()) {
        null, "" -> File(rawUri).takeIf(File::isFile)?.inputStream()
        "file" -> uri.path?.let(::File)?.takeIf(File::isFile)?.inputStream()
        else -> context.contentResolver.openInputStream(uri)
    }
}

private const val PALETTE_ARTWORK_EDGE_PX = 256
