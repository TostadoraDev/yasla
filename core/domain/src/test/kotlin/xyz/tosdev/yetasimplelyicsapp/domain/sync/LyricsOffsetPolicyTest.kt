package xyz.tosdev.yetasimplelyicsapp.domain.sync

import org.junit.Assert.*
import org.junit.Test
import xyz.tosdev.yetasimplelyicsapp.domain.lyrics.*
import xyz.tosdev.yetasimplelyicsapp.domain.lyrics.parser.*
import xyz.tosdev.yetasimplelyicsapp.domain.playback.*

class LyricsOffsetPolicyTest {
    private fun parsed(apple: Boolean) = TtmlLyricsParser().parse(
        """<tt xmlns="http://www.w3.org/ns/ttml" ${if (apple) "xmlns:i=\"http://music.apple.com/lyric-ttml-internal\" i:timing=\"Word\"" else ""}><body><div><p begin="1000ms" end="2000ms"><span begin="1000ms" end="2000ms">hello</span></p></div></body></tt>""",
        LyricsParseContext("doc", "track", LyricsSource("local"))).document!!

    @Test fun onlyRecognizedAppleTimingReceivesTheDefault() {
        assertEquals(250L, LyricsOffsetPolicy.resolve(parsed(true), null))
        assertEquals(0L, LyricsOffsetPolicy.resolve(parsed(false), null))
        assertEquals(0L, LyricsOffsetPolicy.resolve(null, null))
    }
    @Test fun explicitZeroAndSongOverridesAlwaysWinAndReloadDoesNotAccumulate() {
        val doc = parsed(true)
        for (saved in listOf(0L, -150L, 375L)) {
            repeat(3) { assertEquals(saved, LyricsOffsetPolicy.resolve(doc, saved)) }
        }
        repeat(3) { assertEquals(250L, LyricsOffsetPolicy.resolve(doc, null)) }
        assertEquals(75L, LyricsOffsetPolicy.resolve(parsed(false), 75L))
    }
    @Test fun positiveOffsetDelaysOneSharedTimelineWithoutEditingTimestamps() {
        var now = 1249L
        val doc = parsed(true)
        val engine = LyricsSyncEngine(MonotonicClock { now })
        engine.updatePlayback(PlaybackSnapshot("session", 1, "track", PlaybackStatus.PLAYING, 0, 0))
        engine.updateOffset(LyricsOffsetPolicy.resolve(doc, null))
        engine.updateLyrics(doc)
        assertEquals(999L, engine.sample().lyricsPositionMs)
        assertNull(engine.sample().activeWord)
        now = 1250
        assertEquals("hello", engine.sample().activeWord?.text)
        assertEquals(1000L, doc.lines.single().words.single().startTimeMs)
        engine.updateOffset(0)
        assertEquals(1250L, engine.sample().lyricsPositionMs)
    }
}
