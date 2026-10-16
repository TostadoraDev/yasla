package xyz.tosdev.yetasimplelyicsapp

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import xyz.tosdev.yetasimplelyicsapp.data.preferences.AppPreferences
import xyz.tosdev.yetasimplelyicsapp.domain.lyrics.LyricsSource
import xyz.tosdev.yetasimplelyicsapp.domain.lyrics.parser.*
import xyz.tosdev.yetasimplelyicsapp.domain.sync.LyricsOffsetPolicy

@RunWith(AndroidJUnit4::class)
class OffsetPersistenceInstrumentedTest {
    @Test fun freshDefaultAndExplicitZeroSurvivePreferenceReloads() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val key = "${context.packageName}:offset-owned-test"
        val prefs = AppPreferences(context)
        val doc = TtmlLyricsParser().parse("""<tt xmlns="http://www.w3.org/ns/ttml" xmlns:i="http://music.apple.com/lyric-ttml-internal" i:timing="Word"><body><div><p begin="1s" end="2s"><span begin="1s" end="2s">hello</span></p></div></body></tt>""",
            LyricsParseContext("offset-owned", key, LyricsSource("local"))).document!!
        try {
            prefs.clearLyricsOffset(key)
            assertNull(prefs.lyricsOffsetOverride(key).first())
            assertEquals(250L, LyricsOffsetPolicy.resolve(doc, prefs.lyricsOffsetOverride(key).first()))
            for (offset in listOf(0L, -150L, 375L)) {
                prefs.setLyricsOffset(key, offset)
                repeat(3) {
                    val reloaded = AppPreferences(context).lyricsOffsetOverride(key).first()
                    assertEquals(offset, LyricsOffsetPolicy.resolve(doc, reloaded))
                }
            }
            assertEquals(1000L, doc.lines.single().words.single().startTimeMs)
        } finally { prefs.clearLyricsOffset(key) }
    }
}
