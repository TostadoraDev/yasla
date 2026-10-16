package xyz.tosdev.yetasimplelyicsapp

import android.os.Bundle
import android.os.Build
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import xyz.tosdev.yetasimplelyicsapp.domain.lyrics.LyricsSource
import xyz.tosdev.yetasimplelyicsapp.domain.lyrics.parser.LyricsParseContext
import xyz.tosdev.yetasimplelyicsapp.domain.lyrics.parser.TtmlLyricsParser
import xyz.tosdev.yetasimplelyicsapp.ui.LyricsTimeline
import xyz.tosdev.yetasimplelyicsapp.ui.MotionLevel
import xyz.tosdev.yetasimplelyicsapp.ui.LyricLineUi
import xyz.tosdev.yetasimplelyicsapp.ui.KaraokeWordUi
import xyz.tosdev.yetasimplelyicsapp.domain.lyrics.LyricsAlignment
import xyz.tosdev.yetasimplelyicsapp.domain.lyrics.LyricsTextDirection
import xyz.tosdev.yetasimplelyicsapp.ui.components.AutoScrollingLyrics
import xyz.tosdev.yetasimplelyicsapp.ui.presentationLines

/** Deterministic, debug-only fixture; does not drive MediaSession or any production clock. */
class AmllMotionTestActivity : ComponentActivity() {
    private val timeline = LyricsTimeline(0L)
    private var timeMs by mutableLongStateOf(0L)
    private var playing by mutableStateOf(true)
    private var level by mutableStateOf(MotionLevel.NORMAL)
    private var previewLines by mutableStateOf<List<LyricLineUi>?>(null)

    fun setRtlSample() {
        previewLines = listOf(LyricLineUi("rtl", listOf(
            KaraokeWordUi("rtl-word", "العالم", startTimeMs = 0, endTimeMs = 3_000)),
            alignment = LyricsAlignment.RIGHT, direction = LyricsTextDirection.RTL))
        setSample(1_500, isPlaying = false)
    }

    fun setSample(time: Long, isPlaying: Boolean = true, motion: MotionLevel = MotionLevel.NORMAL) {
        timeline.positionMs = time
        timeMs = time
        playing = isPlaying
        level = motion
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        val input = assets.open("amll-motion-fixture.ttml").bufferedReader().use { it.readText() }
        val source = TtmlLyricsParser().parse(input,
            LyricsParseContext("amll-motion-fixture", "amll-motion-fixture", LyricsSource("test")))
            .document!!.presentationLines()
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(
                background = Color.Black, surface = Color.Black, onSurface = Color.White,
            )) {
                Column(Modifier.fillMaxSize().background(Color.Black).padding(24.dp)) {
                    Text("AMLL native · $timeMs ms · ${if (playing) "playing" else "paused"}",
                        color = Color.White.copy(alpha = 0.5f))
                    val lines = (previewLines ?: source).map { line -> line.copy(
                        isActive = timeMs >= (line.startTimeMs ?: Long.MAX_VALUE) &&
                            timeMs < (line.endTimeMs ?: Long.MIN_VALUE),
                        isPast = timeMs >= (line.endTimeMs ?: Long.MAX_VALUE),
                    ) }
                    val active = lines.firstOrNull { it.isActive }?.id ?: lines.lastOrNull {
                        (it.startTimeMs ?: Long.MAX_VALUE) <= timeMs
                    }?.id
                    AutoScrollingLyrics(lines, active, level, Modifier.weight(1f),
                        showTranslation = false, timeline = timeline, isPlaying = playing)
                }
            }
        }
    }
}
