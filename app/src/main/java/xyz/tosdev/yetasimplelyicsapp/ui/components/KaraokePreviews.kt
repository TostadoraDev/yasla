package xyz.tosdev.yetasimplelyicsapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import xyz.tosdev.yetasimplelyicsapp.domain.lyrics.LyricsAlignment
import xyz.tosdev.yetasimplelyicsapp.ui.KaraokeWordUi
import xyz.tosdev.yetasimplelyicsapp.ui.LyricLineUi

@Preview(name = "Solo · word synced", widthDp = 390, showBackground = true)
@Composable
private fun SoloWordSyncedPreview() = PreviewSurface {
    KaraokeText(
        line = previewLine(
            id = "solo",
            text = "I know you're somewhere out there",
            alignment = LyricsAlignment.LEFT,
            words = timedWords("I", "know", "you're", "somewhere", "out", "there"),
        ),
        positionMs = { 1_620L },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Preview(name = "Duet · stable sides", widthDp = 390, showBackground = true)
@Composable
private fun DuetPreview() = PreviewSurface {
    Column {
        KaraokeText(
            line = previewLine(
                "singer-a",
                "I have been waiting",
                LyricsAlignment.LEFT,
                timedWords("I", "have", "been", "waiting")
            ),
            positionMs = { 1_250L },
            modifier = Modifier.fillMaxWidth(),
        )
        KaraokeText(
            line = previewLine(
                "singer-b",
                "And I heard your answer",
                LyricsAlignment.RIGHT,
                timedWords("And", "I", "heard", "your", "answer")
            ),
            positionMs = { 700L },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 18.dp),
        )
    }
}

@Preview(name = "Background vocals", widthDp = 390, showBackground = true)
@Composable
private fun BackgroundVocalsPreview() = PreviewSurface {
    KaraokeText(
        line = previewLine(
            id = "background",
            text = "(stay with me tonight)",
            alignment = LyricsAlignment.LEFT,
            words = timedWords("stay", "with", "me", "tonight"),
            background = true,
        ),
        positionMs = { 1_460L },
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 18.dp),
        textStyle = MaterialTheme.typography.headlineSmall,
        inactiveTextAlpha = 0.74f,
    )
}

@Preview(name = "Long wrapped line", widthDp = 320, showBackground = true)
@Composable
private fun LongLinePreview() = PreviewSurface {
    KaraokeText(
        line = previewLine(
            "long",
            "A long lyric should wrap naturally without clipping its second or third visual line",
            LyricsAlignment.LEFT,
            timedWords(
                "A",
                "long",
                "lyric",
                "should",
                "wrap",
                "naturally",
                "without",
                "clipping",
                "its",
                "second",
                "or",
                "third",
                "visual",
                "line"
            ),
        ),
        positionMs = { 2_340L },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Preview(name = "Short centered cue", widthDp = 390, showBackground = true)
@Composable
private fun ShortLinePreview() = PreviewSurface {
    KaraokeText(
        line = previewLine("short", "Breathe", LyricsAlignment.CENTER, timedWords("Breathe")),
        positionMs = { 540L },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Preview(name = "Line synced fallback", widthDp = 390, showBackground = true)
@Composable
private fun LineSyncedPreview() = PreviewSurface {
    KaraokeText(
        line = LyricLineUi(
            id = "line-sync",
            words = emptyList(),
            originalText = "Line timing activates the full line uniformly",
            isActive = true,
            startTimeMs = 1_000L,
            endTimeMs = 5_000L,
            alignment = LyricsAlignment.LEFT,
        ),
        positionMs = { 3_100L },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun PreviewSurface(content: @Composable () -> Unit) {
    MaterialTheme {
        Column(
            modifier = Modifier
                .background(Color(0xFF16181C))
                .padding(22.dp),
        ) { content() }
    }
}

private fun previewLine(
    id: String,
    text: String,
    alignment: LyricsAlignment,
    words: List<KaraokeWordUi>,
    background: Boolean = false,
) = LyricLineUi(
    id = id,
    words = words,
    originalText = text,
    isBackground = background,
    isActive = true,
    startTimeMs = 0L,
    endTimeMs = words.lastOrNull()?.endTimeMs ?: 3_000L,
    alignment = alignment,
    sourceAlignment = alignment,
)

private fun timedWords(vararg words: String): List<KaraokeWordUi> =
    words.mapIndexed { index, word ->
        KaraokeWordUi(
            id = "word-$index",
            text = word,
            startTimeMs = index * 420L,
            endTimeMs = (index + 1) * 420L,
        )
    }
