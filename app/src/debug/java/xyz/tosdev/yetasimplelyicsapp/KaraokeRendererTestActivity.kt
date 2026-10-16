package xyz.tosdev.yetasimplelyicsapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import xyz.tosdev.yetasimplelyicsapp.domain.lyrics.LyricsAlignment
import xyz.tosdev.yetasimplelyicsapp.domain.lyrics.LyricsTextDirection
import xyz.tosdev.yetasimplelyicsapp.ui.HighlightStyle
import xyz.tosdev.yetasimplelyicsapp.ui.InactiveStyle
import xyz.tosdev.yetasimplelyicsapp.ui.KaraokeSegmentUi
import xyz.tosdev.yetasimplelyicsapp.ui.KaraokeVisualStyle
import xyz.tosdev.yetasimplelyicsapp.ui.KaraokeWordUi
import xyz.tosdev.yetasimplelyicsapp.ui.LyricLineUi
import xyz.tosdev.yetasimplelyicsapp.ui.LyricsTimeline
import xyz.tosdev.yetasimplelyicsapp.ui.MotionLevel
import xyz.tosdev.yetasimplelyicsapp.ui.components.CurrentWordLyrics
import xyz.tosdev.yetasimplelyicsapp.ui.components.KaraokeText

class KaraokeRendererTestActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { KaraokeRendererRegressionScene() }
    }
}

@Composable
private fun KaraokeRendererRegressionScene() {
    val style = KaraokeVisualStyle(
        highlight = HighlightStyle.CUSTOM_GRADIENT,
        inactive = InactiveStyle.CUSTOM,
        customStartArgb = 0xFFFFFFFF,
        customEndArgb = 0xFFDCEBFF,
        inactiveArgb = 0xFF4C5159,
        featherDp = 10f,
    )
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = Color.Black,
            surface = Color.Black,
            onSurface = Color.White,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .padding(horizontal = 24.dp, vertical = 36.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            RegressionCurrentWord(style)
            RegressionKaraokeLine(
                text = "Quiero que te quiero de verdad aunque la noche empiece a envolvernos lentamente",
                alignment = LyricsAlignment.LEFT,
                fontSize = 42,
                positionMs = 1_725L,
                style = style,
            )
            RegressionKaraokeLine(
                text = "Right aligned English still reveals from left to right across a wrapped line",
                alignment = LyricsAlignment.RIGHT,
                fontSize = 31,
                positionMs = 1_150L,
                style = style,
            )
            RegressionKaraokeLine(
                text = "هذا اختبار عربي طويل يلتف على سطرين",
                alignment = LyricsAlignment.RIGHT,
                direction = LyricsTextDirection.RTL,
                fontSize = 28,
                positionMs = 750L,
                style = style,
            )
            RegressionKaraokeLine(
                text = "empiezas otra vez",
                alignment = LyricsAlignment.LEFT,
                fontSize = 24,
                positionMs = 620L,
                style = style,
                reconstructFirstWord = true,
            )
        }
    }
}

@Composable
private fun RegressionCurrentWord(style: KaraokeVisualStyle) {
    val word = KaraokeWordUi(
        id = "current-empiezas",
        text = "empiezas",
        startTimeMs = 0L,
        endTimeMs = 1_000L,
        segments = listOf(
            KaraokeSegmentUi("current-em", "em", 0L, 240L),
            KaraokeSegmentUi("current-pie", "pie", 240L, 680L),
            KaraokeSegmentUi("current-zas", "zas", 680L, 1_000L),
        ),
    )
    CurrentWordLyrics(
        activeLine = LyricLineUi(
            id = "current-line",
            words = listOf(word),
            originalText = "empiezas",
            isActive = true,
            startTimeMs = 0L,
            endTimeMs = 1_000L,
            alignment = LyricsAlignment.LEFT,
            direction = LyricsTextDirection.LTR,
        ),
        activeWordId = word.id,
        timeline = LyricsTimeline(620L),
        fallbackToLine = true,
        motionLevel = MotionLevel.OFF,
        visualStyle = style,
        artworkUri = null,
        modifier = Modifier.fillMaxWidth(),
        textStyle = TextStyle(fontSize = 54.sp, lineHeight = 62.sp),
    )
}

@Composable
private fun RegressionKaraokeLine(
    text: String,
    alignment: LyricsAlignment,
    fontSize: Int,
    positionMs: Long,
    style: KaraokeVisualStyle,
    direction: LyricsTextDirection = LyricsTextDirection.LTR,
    reconstructFirstWord: Boolean = false,
) {
    val words = text.split(' ').mapIndexed { index, word ->
        val start = index * 300L
        val end = start + 300L
        KaraokeWordUi(
            id = "${alignment.name}-$fontSize-$index",
            text = word,
            startTimeMs = start,
            endTimeMs = end,
            segments = if (reconstructFirstWord && index == 0) {
                listOf(
                    KaraokeSegmentUi("em", "em", start, start + 80L),
                    KaraokeSegmentUi("pie", "pie", start + 80L, start + 200L),
                    KaraokeSegmentUi("zas", "zas", start + 200L, end),
                )
            } else {
                emptyList()
            },
        )
    }
    KaraokeText(
        line = LyricLineUi(
            id = "${alignment.name}-$fontSize",
            words = words,
            originalText = text,
            isActive = true,
            startTimeMs = 0L,
            endTimeMs = words.last().endTimeMs,
            alignment = alignment,
            sourceAlignment = alignment,
            direction = direction,
        ),
        positionMs = { positionMs },
        modifier = Modifier.fillMaxWidth(),
        textStyle = TextStyle(fontSize = fontSize.sp, lineHeight = (fontSize + 8).sp),
        visualStyle = style,
        alignment = alignment,
        motionLevel = MotionLevel.OFF,
    )
}
