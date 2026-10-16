package xyz.tosdev.yetasimplelyicsapp.widget

import org.junit.Assert.*
import org.junit.Test
import xyz.tosdev.yetasimplelyicsapp.domain.lyrics.LyricsSource
import xyz.tosdev.yetasimplelyicsapp.domain.lyrics.parser.LyricsParseContext
import xyz.tosdev.yetasimplelyicsapp.domain.lyrics.parser.TtmlLyricsParser
import xyz.tosdev.yetasimplelyicsapp.domain.playback.MonotonicClock
import xyz.tosdev.yetasimplelyicsapp.domain.playback.PlaybackSnapshot
import xyz.tosdev.yetasimplelyicsapp.domain.playback.PlaybackStatus
import xyz.tosdev.yetasimplelyicsapp.domain.sync.LyricsSyncEngine

class SemanticWidgetTimelineTest {
    @Test fun parserToClockToWidgetChangesOnlyAtTheNextWholeWordIncludingGapsAndBackwardSeek() {
        var time = 0L
        val pieces = listOf(listOf("cuan", "do"), listOf("qui", "e", "re"), listOf("mien", "tras"),
            listOf("ex", "tra", "or", "di", "na", "rio"))
        val spans = pieces.mapIndexed { index, fragments ->
            fragments.mapIndexed { segment, text ->
                "<span begin=\"${index * 2000 + segment * 100}ms\" end=\"${index * 2000 + (segment + 1) * 100}ms\">$text</span>"
            }.joinToString("")
        }.joinToString(" ")
        val result = TtmlLyricsParser().parse("""<tt xmlns="http://www.w3.org/ns/ttml" xmlns:itunes="http://music.apple.com/lyric-ttml-internal" itunes:timing="Word" xml:lang="es"><body><div><p begin="0ms" end="10000ms">$spans</p></div></body></tt>""",
            LyricsParseContext("test", "track", LyricsSource("local")))
        val document = requireNotNull(result.document)
        val words = document.lines.single().words
        assertEquals(listOf("cuando", "quiere", "mientras", "extraordinario"), words.map { it.text })
        val engine = LyricsSyncEngine(MonotonicClock { time })
        engine.updatePlayback(PlaybackSnapshot("session", 1, "track", PlaybackStatus.PLAYING, 0, 0))
        engine.updateLyrics(document)
        val config = LyricsWidgetConfig(contentMode = LyricsWidgetContentMode.CURRENT_WORD)
        for (position in listOf(0L, 99L, 100L, 199L, 201L, 1900L, 2000L, 2100L, 2200L, 3999L, 4000L, 6000L, 6500L, 6999L, 2100L)) {
            time = position
            val sync = engine.sample()
            val expected = words[(position / 2000).toInt().coerceAtMost(3)]
            val word = resolveStableWidgetWord(sync.activeWord, sync.lyricsPositionMs, words)!!
            assertEquals(expected.id, word.id)
            val snapshot = LyricsWidgetSnapshot(trackKey = "track", activeWordId = word.id, activeWord = word.text,
                hasReliableWordSync = true)
            assertEquals(snapshot.lineRenderSignature(config), snapshot.copy(capturedAtElapsedMs = time + 100).lineRenderSignature(config))
            for (size in listOf(60f to 65f, 150f to 100f, 300f to 200f)) {
                val layout = WordLineBreaker.layoutWord(word.text, "es", java.util.Locale.ROOT,
                    availableWidthDp = size.first, availableHeightDp = size.second,
                    preferredFontSizeSp = 32f, minFontSizeSp = 24f, maxLines = 4)
                assertEquals(word.text.uppercase(), layout.text.replace("\n", ""))
                assertFalse(layout.ellipsized)
                assertFalse(layout.text.contains("..."))
            }
        }
    }

    @Test fun absoluteBoundariesSkipMissedWordsOverTenMinutesWithoutAccumulatingTimerDrift() {
        val boundaries = LongArray(1201) { it * 500L }
        var monotonic = 0L
        val sample = PlaybackSnapshot("session", 1, "track", PlaybackStatus.PLAYING, 0, 0, speed = 1.25f)
        var iteration = 0
        while (sample.estimatedPositionAt(monotonic)!! < 600_250L) {
            val position = sample.estimatedPositionAt(monotonic)!! - 250L
            val wait = xyz.tosdev.yetasimplelyicsapp.nextCueBoundaryDelayMs(boundaries, position, sample.speed)
                ?: break
            // Simulated work and suspension sometimes miss several words. Resample; no replay.
            monotonic += wait + if (iteration++ % 13 == 0) 1700 else 35
            val resumedPosition = sample.estimatedPositionAt(monotonic)!! - 250L
            val resumedWait = xyz.tosdev.yetasimplelyicsapp.nextCueBoundaryDelayMs(boundaries, resumedPosition, sample.speed)
            if (resumedWait != null) assertTrue(resumedWait in 1L..400L)
        }
        assertTrue(monotonic > 480_000L)
    }
}
