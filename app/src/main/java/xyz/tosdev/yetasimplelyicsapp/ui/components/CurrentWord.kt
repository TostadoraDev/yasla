package xyz.tosdev.yetasimplelyicsapp.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import xyz.tosdev.yetasimplelyicsapp.domain.lyrics.LyricsAlignment
import xyz.tosdev.yetasimplelyicsapp.ui.KaraokeSegmentUi
import xyz.tosdev.yetasimplelyicsapp.ui.KaraokeVisualStyle
import xyz.tosdev.yetasimplelyicsapp.ui.KaraokeWordUi
import xyz.tosdev.yetasimplelyicsapp.ui.LyricLineUi
import xyz.tosdev.yetasimplelyicsapp.ui.LyricsTimeline
import xyz.tosdev.yetasimplelyicsapp.ui.LyricsTimingMode
import xyz.tosdev.yetasimplelyicsapp.ui.MotionLevel
import xyz.tosdev.yetasimplelyicsapp.ui.designsystem.SignalMotionScheme
import xyz.tosdev.yetasimplelyicsapp.ui.designsystem.SignalMotionSpecs
import xyz.tosdev.yetasimplelyicsapp.ui.designsystem.rememberSignalMotion
import java.util.Locale

@Immutable
internal data class CurrentWordContent(
    val id: String,
    val line: LyricLineUi,
    val isWordSynced: Boolean,
)

internal fun resolveCurrentWordContent(
    activeLine: LyricLineUi?,
    activeWordId: String?,
    fallbackToLine: Boolean,
): CurrentWordContent? {
    val line = activeLine ?: return null
    if (line.timingMode == LyricsTimingMode.WORD_SYNCED) {
        val word = line.words.firstOrNull { it.id == activeWordId } ?: return null
        return CurrentWordContent(
            id = word.id,
            line = line.asCurrentWordLine(word),
            isWordSynced = true,
        )
    }
    if (!fallbackToLine) return null
    return CurrentWordContent(
        id = "${line.id}:line-fallback",
        line = line.copy(words = emptyList(), isActive = true),
        isWordSynced = false,
    )
}

@Composable
fun CurrentWordLyrics(
    activeLine: LyricLineUi?,
    activeWordId: String?,
    timeline: LyricsTimeline?,
    fallbackToLine: Boolean,
    motionLevel: MotionLevel,
    visualStyle: KaraokeVisualStyle,
    artworkUri: String?,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = MaterialTheme.typography.displaySmall,
    contentAlpha: Float = 1f,
    colors: KaraokeColors? = null,
) {
    val motion = rememberSignalMotion(motionLevel)
    val content = resolveCurrentWordContent(activeLine, activeWordId, fallbackToLine)
    val locale = LocalConfiguration.current.locales[0]
    val displayContent = remember(content, locale) {
        content?.uppercaseForPresentation(locale)
    }
    val transitionOffsetPx = with(LocalDensity.current) { 4.dp.roundToPx() }
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        AnimatedContent(
            targetState = displayContent,
            contentKey = { it?.id ?: "unavailable" },
            transitionSpec = { currentWordTransition(motion, transitionOffsetPx) },
            label = "current word",
        ) { current ->
            if (current != null) {
                KaraokeText(
                    line = current.line,
                    positionMs = { timeline?.positionMs },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = textStyle,
                    visualStyle = visualStyle,
                    alignment = LyricsAlignment.CENTER,
                    motionLevel = motionLevel,
                    artworkUri = artworkUri,
                    isActive = true,
                    activeForegroundAlpha = contentAlpha.coerceIn(0f, 1f),
                    inactiveTextAlpha = (if (current.isWordSynced) 1f else 0.94f) *
                            contentAlpha.coerceIn(0f, 1f),
                    colors = colors,
                )
            }
        }
    }
}

internal fun CurrentWordContent.uppercaseForPresentation(locale: Locale): CurrentWordContent = copy(
    line = line.copy(
        originalText = line.plainText.uppercase(locale),
        words = line.words.map { word ->
            word.copy(
                text = word.text.uppercase(locale),
                segments = word.segments.map { segment ->
                    segment.uppercaseForPresentation(locale)
                },
            )
        },
    ),
)

private fun KaraokeSegmentUi.uppercaseForPresentation(locale: Locale): KaraokeSegmentUi =
    copy(text = text.uppercase(locale))

private fun LyricLineUi.asCurrentWordLine(word: KaraokeWordUi): LyricLineUi = copy(
    id = "${id}:${word.id}",
    words = listOf(word),
    originalText = word.text,
    translation = null,
    isActive = true,
    isPast = false,
    startTimeMs = word.startTimeMs,
    endTimeMs = word.endTimeMs,
)

private fun currentWordTransition(
    motion: SignalMotionScheme,
    offsetPx: Int,
): ContentTransform = when {
    !motion.enabled -> EnterTransition.None togetherWith ExitTransition.None
    motion.reduced -> fadeIn(SignalMotionSpecs.standard(motion)) togetherWith
        fadeOut(SignalMotionSpecs.exit(motion))
    else -> (
        fadeIn(SignalMotionSpecs.content(motion)) +
            slideInVertically(SignalMotionSpecs.content(motion)) { offsetPx } +
            scaleIn(initialScale = 0.985f, animationSpec = SignalMotionSpecs.content(motion))
        ) togetherWith (
        fadeOut(SignalMotionSpecs.exit(motion)) +
            slideOutVertically(SignalMotionSpecs.exit(motion)) { -offsetPx } +
            scaleOut(targetScale = 0.99f, animationSpec = SignalMotionSpecs.exit(motion))
        )
}
