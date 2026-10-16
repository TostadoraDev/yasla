package xyz.tosdev.yetasimplelyicsapp.ui.components

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.ResolvedTextDirection
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import xyz.tosdev.yetasimplelyicsapp.domain.lyrics.LyricsAlignment
import xyz.tosdev.yetasimplelyicsapp.domain.lyrics.LyricsTextDirection
import xyz.tosdev.yetasimplelyicsapp.ui.LyricLineUi
import xyz.tosdev.yetasimplelyicsapp.ui.LyricsTimingMode
import xyz.tosdev.yetasimplelyicsapp.ui.MotionLevel
import java.text.BreakIterator
import java.util.Locale

internal data class AmllTimedWidth(val startMs: Long, val endMs: Long, val widthPx: Float)

/** Measured ink advances linearly between the original temporal control points. */
internal fun amllRevealDistanceAt(
    timeMs: Long,
    pieces: List<AmllTimedWidth>,
    featherPx: Float
): Float {
    var distance = 0f
    pieces.forEachIndexed { index, piece ->
        val extra = (if (index == 0) featherPx * 1.5f else 0f) +
                (if (index == pieces.lastIndex) featherPx * 0.5f else 0f)
        val progress = ((timeMs - piece.startMs).toDouble() /
                (piece.endMs - piece.startMs).coerceAtLeast(1L)).coerceIn(0.0, 1.0).toFloat()
        distance += (piece.widthPx + extra) * progress
    }
    return distance - featherPx * 2f
}

private data class AmllInkFragment(
    val bounds: Rect,
    val wordIndex: Int,
    val characterIndex: Int,
    val characterCount: Int,
    val direction: ResolvedTextDirection,
    val inkStart: Float,
    val startOffset: Int,
    val endOffset: Int,
)

/** Dedicated native path for Now Playing. The overlay's renderer and masks are not involved. */
@Composable
internal fun AmllKaraokeText(
    line: LyricLineUi,
    row: AmllRowMotion,
    positionMs: () -> Long?,
    textStyle: TextStyle,
    colors: KaraokeColors,
    alignment: LyricsAlignment,
    motionLevel: MotionLevel,
    modifier: Modifier = Modifier,
) {
    val text = line.plainText
    var layout by remember(text, textStyle, alignment, line.direction) {
        mutableStateOf<TextLayoutResult?>(null)
    }
    val words = remember(line.words, text) { resolveSemanticWordEntrances(line, isActive = true) }
    val segments = remember(line.words, text) { resolveTimedTextSegments(line, isActive = true) }
    val fragments = remember(layout, words) {
        layout?.let { measured -> buildAmllInk(measured, words, line) }.orEmpty()
    }
    val measurer = rememberTextMeasurer(cacheSize = 0)
    // Real isolated fragments: overriding drawText's brush can override transparent span paint.
    // Keep the paragraph's measured anchors/baselines, but never draw its neighboring glyphs.
    val fragmentLayouts = remember(layout, fragments, measurer) {
        val measured = layout
        if (measured == null) emptyList() else fragments.map { fragment ->
            measurer.measure(
                text = text.substring(fragment.startOffset, fragment.endOffset),
                style = measured.layoutInput.style.copy(
                    color = Color.White,
                    textAlign = TextAlign.Left,
                    textDirection = if (fragment.direction == ResolvedTextDirection.Rtl)
                        TextDirection.Rtl else TextDirection.Ltr
                ),
                softWrap = false,
                maxLines = 1,
            )
        }
    }
    val timedWidths = remember(layout, segments) {
        val measured = layout
        if (measured == null) emptyList() else segments.mapNotNull { segment ->
            val start = segment.startTimeMs ?: return@mapNotNull null
            val end = segment.endTimeMs ?: return@mapNotNull null
            val bounds = measured.amllRangeBounds(segment.startOffset, segment.endOffset)
            // Sum each visual row separately. A bounding rectangle across wrapping includes void.
            val width = bounds.sumOf { it.width.toDouble() }.toFloat()
            AmllTimedWidth(start, end, width)
        }
    }
    val direction = when (line.direction) {
        LyricsTextDirection.LTR -> TextDirection.Ltr
        LyricsTextDirection.RTL -> TextDirection.Rtl
        LyricsTextDirection.AUTO -> TextDirection.Content
    }
    BasicText(
        text = text,
        onTextLayout = { next ->
            val previous = layout
            if (previous == null || previous.layoutInput != next.layoutInput ||
                previous.size != next.size || previous.lineCount != next.lineCount ||
                previous.firstBaseline != next.firstBaseline || previous.lastBaseline != next.lastBaseline ||
                previous.multiParagraph.intrinsics.hasStaleResolvedFonts ||
                (0 until next.lineCount).any { visualLine ->
                    previous.getLineLeft(visualLine) != next.getLineLeft(visualLine) ||
                        previous.getLineRight(visualLine) != next.getLineRight(visualLine) ||
                        previous.getLineEnd(visualLine) != next.getLineEnd(visualLine)
                }) {
                layout = next
            }
        },
        style = textStyle.copy(
            color = Color.Transparent,
            background = Color.Unspecified,
            textAlign = alignment.asTextAlign(),
            textDirection = direction,
        ),
        modifier = modifier
            .semantics { contentDescription = text }
            .drawWithCache {
                val measured = layout
                val em =
                    if (textStyle.fontSize != TextUnit.Unspecified) textStyle.fontSize.toPx() else 36f * density
                // AMLL's default wordFadeWidth is half the measured word height.
                val feather =
                    (measured?.let { it.getLineBottom(0) - it.getLineTop(0) } ?: em) * 0.5f
                val glowRadius = (em * 0.065f).coerceAtMost(2.5.dp.toPx())
                onDrawBehind {
                    val result = measured ?: return@onDrawBehind
                    val frame = row.frame
                    // Inactive masks freeze; do not subscribe every future row to media frames.
                    val now = if (row.active && line.timingMode == LyricsTimingMode.WORD_SYNCED)
                        positionMs() else row.frozenWordPosition()
                    if (line.timingMode != LyricsTimingMode.WORD_SYNCED || fragments.isEmpty()) {
                        drawText(result, color = colors.highlight.first())
                        return@onDrawBehind
                    }
                    val emphasisTime = row.emphasisPosition(now)
                    val reveal =
                        amllRevealDistanceAt(now ?: Long.MIN_VALUE / 2, timedWidths, feather)
                    fragments.forEachIndexed { fragmentIndex, fragment ->
                        val isolated = fragmentLayouts[fragmentIndex]
                        val word = words[fragment.wordIndex]
                        val source = line.words[word.sourceWordIndex]
                        val start = word.startTimeMs ?: 0L
                        val end = word.endTimeMs ?: start
                        val pose = amllWordPose(
                            elapsedMs = emphasisTime?.minus(start) ?: -1L,
                            baseElapsedMs = row.baselineElapsed(now, start, end - start) ?: -1L,
                            durationMs = end - start,
                            fontSizePx = em,
                            text = source.text,
                            isBackground = line.isBackground,
                            isLastWord = fragment.wordIndex == words.lastIndex,
                            level = motionLevel,
                            characterIndex = fragment.characterIndex,
                            characterCount = fragment.characterCount,
                        )
                        val b = fragment.bounds
                        val boundary = reveal - fragment.inkStart
                        val rtl = fragment.direction == ResolvedTextDirection.Rtl
                        fun pixelColor(x: Float): Color {
                            val inkX = if (rtl) b.right - x else x - b.left
                            val illuminated =
                                amllIlluminatedFraction(inkX, boundary, feather)
                            val alpha =
                                amllKaraokeAlpha(frame.darkAlpha, frame.brightAlpha, illuminated, row.active)
                            val tint = lerp(
                                colors.highlight.first(), colors.highlight.last(),
                                (x / result.size.width.coerceAtLeast(1)).coerceIn(0f, 1f)
                            )
                            return tint.copy(alpha = tint.alpha * alpha)
                        }

                        val x0 = if (rtl) b.right - boundary - feather else b.left + boundary
                        val x1 = if (rtl) b.right - boundary else b.left + boundary + feather
                        val cuts = listOf(
                            b.left, x0.coerceIn(b.left, b.right),
                            x1.coerceIn(b.left, b.right), b.right
                        ).distinct().sorted()
                        val brush = Brush.horizontalGradient(
                            colorStops = cuts.map { x ->
                                ((x - b.left) / b.width.coerceAtLeast(
                                    0.001f
                                )) to pixelColor(x)
                            }.toTypedArray(),
                            startX = 0f,
                            endX = isolated.getLineRight(0) - isolated.getLineLeft(0),
                        )
                        withTransform({
                            translate(pose.translationXPx, pose.translationYPx)
                            scale(pose.scale, pose.scale, pivot = b.center)
                        }) {
                            val anchor = Offset(
                                b.left - isolated.getLineLeft(0),
                                result.getLineBaseline(result.getLineForOffset(fragment.startOffset)) - isolated.firstBaseline,
                            )
                            val glowPeak = amllGlowStrength(
                                amllIlluminatedFraction(0f, boundary, feather), row.active, motionLevel,
                            )
                            if (glowPeak > 0f) {
                                val lightWidth = (boundary + feather).coerceIn(0f, b.width)
                                val glowBrush = Brush.horizontalGradient(
                                    colorStops = cuts.map { x ->
                                        val inkX = if (rtl) b.right - x else x - b.left
                                        ((x - b.left) / b.width.coerceAtLeast(0.001f)) to
                                            lerp(colors.highlight.first(), Color.White, 0.3f).copy(
                                                alpha = amllGlowStrength(
                                                    amllIlluminatedFraction(inkX, boundary, feather),
                                                    row.active, motionLevel,
                                                ),
                                            )
                                    }.toTypedArray(),
                                    startX = 0f,
                                    endX = isolated.getLineRight(0) - isolated.getLineLeft(0),
                                )
                                // Bound native shadow paint to lit ink, including only its small halo.
                                // The brush itself has zero alpha over pending glyphs.
                                clipRect(
                                    left = if (rtl) b.right - lightWidth - glowRadius else b.left - glowRadius,
                                    right = if (rtl) b.right + glowRadius else b.left + lightWidth + glowRadius,
                                    top = b.top - glowRadius, bottom = b.bottom + glowRadius,
                                ) {
                                    drawText(isolated, brush = glowBrush, topLeft = anchor,
                                        shadow = Shadow(colors.highlight.first().copy(alpha = glowPeak),
                                            Offset.Zero, glowRadius))
                                }
                            }
                            drawText(
                                isolated,
                                brush = brush,
                                topLeft = anchor,
                                shadow = Shadow.None,
                            )
                        }
                    }
                }
            },
    )
}

private fun buildAmllInk(
    layout: TextLayoutResult,
    words: List<SemanticWordEntrance>,
    line: LyricLineUi
): List<AmllInkFragment> {
    var inkCursor = 0f
    return buildList {
        words.forEachIndexed { index, word ->
            val source = layout.layoutInput.text.text
            val end = word.endOffset.coerceAtMost(source.length)
            val content = source.substring(word.startOffset, end)
            val semantic = line.words[word.sourceWordIndex]
            val duration = (word.endTimeMs ?: 0L) - (word.startTimeMs ?: 0L)
            // Keep contextual RTL shaping intact; isolating Arabic characters breaks joining.
            if (!amllCanEmphasize(semantic.text, duration) ||
                layout.getBidiRunDirection(word.startOffset) == ResolvedTextDirection.Rtl
            ) {
                layout.amllRangeInk(word.startOffset, end).forEach { part ->
                    add(
                        AmllInkFragment(
                            part.bounds, index, -1, 1,
                            part.direction, inkCursor, part.startOffset, part.endOffset
                        )
                    )
                    inkCursor += part.bounds.width
                }
                return@forEachIndexed
            }
            val iterator =
                BreakIterator.getCharacterInstance(Locale.ROOT).apply { setText(content) }
            val ranges = mutableListOf<Pair<Int, Int>>()
            var start = iterator.first()
            var next = iterator.next()
            while (next != BreakIterator.DONE) {
                if (content.substring(start, next)
                        .any { !it.isWhitespace() }
                ) ranges += start to next
                start = next
                next = iterator.next()
            }
            ranges.forEachIndexed { character, (a, b) ->
                layout.amllRangeInk(word.startOffset + a, word.startOffset + b).forEach { part ->
                    add(
                        AmllInkFragment(
                            part.bounds, index, character, ranges.size,
                            part.direction, inkCursor, part.startOffset, part.endOffset
                        )
                    )
                    inkCursor += part.bounds.width
                }
            }
        }
    }
}

private fun TextLayoutResult.amllRangeBounds(start: Int, end: Int): List<Rect> {
    return amllRangeInk(start, end).map { it.bounds }
}

private data class AmllRangeInk(
    val bounds: Rect, val startOffset: Int, val endOffset: Int,
    val direction: ResolvedTextDirection, val visualLine: Int,
)

private fun TextLayoutResult.amllRangeInk(start: Int, end: Int): List<AmllRangeInk> {
    val source = layoutInput.text
    val runs = mutableListOf<AmllRangeInk>()
    for (offset in start.coerceAtLeast(0) until end.coerceAtMost(source.length)) {
        if (source[offset].isWhitespace()) continue
        val box = getBoundingBox(offset)
        val visualLine = getLineForOffset(offset)
        val direction = getBidiRunDirection(offset)
        val previous = runs.lastOrNull()
        if (previous != null && previous.visualLine == visualLine && previous.direction == direction &&
            previous.endOffset == offset
        ) {
            val old = previous.bounds
            runs[runs.lastIndex] = previous.copy(
                bounds = if (box.width <= 0f) old else Rect(
                    minOf(old.left, box.left), minOf(old.top, box.top),
                    maxOf(old.right, box.right), maxOf(old.bottom, box.bottom)
                ),
                endOffset = offset + 1,
            )
        } else if (box.width > 0f) {
            runs += AmllRangeInk(box, offset, offset + 1, direction, visualLine)
        }
    }
    return runs
}
