package xyz.tosdev.yetasimplelyicsapp

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import xyz.tosdev.yetasimplelyicsapp.data.importing.LyricsImportReader
import xyz.tosdev.yetasimplelyicsapp.data.local.LyricsDatabase
import xyz.tosdev.yetasimplelyicsapp.data.local.RoomLyricsStore
import xyz.tosdev.yetasimplelyicsapp.data.provider.ImportedLyricsFormat
import xyz.tosdev.yetasimplelyicsapp.data.provider.ImportedLyricsProvider
import xyz.tosdev.yetasimplelyicsapp.domain.lyrics.LyricsRequest
import xyz.tosdev.yetasimplelyicsapp.domain.lyrics.LyricsSource
import xyz.tosdev.yetasimplelyicsapp.domain.lyrics.parser.LyricsParseContext
import xyz.tosdev.yetasimplelyicsapp.domain.lyrics.parser.TtmlLyricsParser

@RunWith(AndroidJUnit4::class)
class TtmlImportInstrumentedTest {
    @Test fun importsWordTimedTtmlWithAndroidParserAndPersistsOffline() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(context, LyricsDatabase::class.java).build()
        try {
            val store = RoomLyricsStore(database.lyricsDao())
            val xml = """<?xml version="1.0" encoding="UTF-8"?>
                <tt xmlns="http://www.w3.org/ns/ttml"><body><div>
                <p begin="00:00:01.000" end="00:00:04.000"><span begin="00:00:01.000" end="00:00:02.000">Hola </span><span begin="00:00:02.000" end="00:00:04.000">mundo</span></p>
                </div></body></tt>"""
            val content = LyricsImportReader.read(xml.toByteArray().inputStream())
            val imported = ImportedLyricsProvider(store).import(
                LyricsRequest("instrumented-track", "Prueba TTML", listOf("Prueba")),
                content, ImportedLyricsFormat.TTML)
            val saved = store.find("instrumented-track")!!
            assertEquals(imported, saved.document)
            assertEquals("Prueba TTML", saved.metadata.title)
            assertEquals(1000L, saved.document.lines.first().startTimeMs)
            assertEquals(2, saved.document.lines.first().words.size)
            assertEquals(2000L, saved.document.lines.first().words[1].startTimeMs)
            assertEquals(1, store.observeLibrary().first().size)
            assertEquals(Long.MAX_VALUE, saved.expiresAtEpochMs)
        } finally { database.close() }
    }

    @Test fun importsAmllWordsTranslationsAndBackgroundVocalsOffline() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val database = Room.inMemoryDatabaseBuilder(
            instrumentation.targetContext, LyricsDatabase::class.java,
        ).build()
        try {
            val content = instrumentation.context.assets.open("amll-compatibility.ttml").use {
                LyricsImportReader.read(it)
            }
            val store = RoomLyricsStore(database.lyricsDao())
            val imported = ImportedLyricsProvider(store).import(
                LyricsRequest("amll-track", "Prueba AMLL", listOf("Prueba")),
                content, ImportedLyricsFormat.TTML,
            )
            val saved = requireNotNull(store.find("amll-track")).document
            assertEquals(imported, saved)
            assertEquals(3, saved.lines.size)
            val main = saved.lines[0]
            val bg = saved.lines[1]
            assertEquals("Sigue aquí", main.text.original)
            assertEquals("Stay here", main.text.translations["en"])
            assertEquals("Sigue aquí", main.text.romanizations["es-Latn"])
            assertEquals(listOf(24_000L, 26_000L), main.words.map { it.startTimeMs })
            assertEquals(main.text.original, main.words.joinToString("") { it.text })
            assertFalse(main.isBackground)
            assertTrue(bg.isBackground)
            assertEquals(main.groupId, bg.groupId)
            assertEquals("here", bg.text.translations["en"])
            assertEquals(27_000L, bg.startTimeMs)
            assertEquals("v2", saved.lines[2].agentId)
        } finally { database.close() }
    }

    @Test fun rejectsExternalEntitiesOnAndroid() {
        val input = """<!DOCTYPE tt [<!ENTITY xxe SYSTEM "file:///data/local/tmp/private.txt">]>
            <tt xmlns="http://www.w3.org/ns/ttml"><body><div><p>&xxe;</p></div></body></tt>"""
        val result = TtmlLyricsParser().parse(input,
            LyricsParseContext("test", "test", LyricsSource("local")))
        assertNull(result.document)
        assertTrue(result.diagnostics.isNotEmpty())
    }
}
