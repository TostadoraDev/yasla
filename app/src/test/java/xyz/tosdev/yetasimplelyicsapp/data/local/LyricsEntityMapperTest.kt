package xyz.tosdev.yetasimplelyicsapp.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.tosdev.yetasimplelyicsapp.domain.lyrics.LocalizedText
import xyz.tosdev.yetasimplelyicsapp.domain.lyrics.LyricsAlignment
import xyz.tosdev.yetasimplelyicsapp.domain.lyrics.LyricsCapability
import xyz.tosdev.yetasimplelyicsapp.domain.lyrics.LyricsDocument
import xyz.tosdev.yetasimplelyicsapp.domain.lyrics.LyricsLine
import xyz.tosdev.yetasimplelyicsapp.domain.lyrics.LyricsSource
import xyz.tosdev.yetasimplelyicsapp.domain.lyrics.LyricsTextDirection
import xyz.tosdev.yetasimplelyicsapp.domain.lyrics.LyricsWord
import xyz.tosdev.yetasimplelyicsapp.domain.lyrics.LyricsWordSegment
import xyz.tosdev.yetasimplelyicsapp.domain.lyrics.SyncType
import xyz.tosdev.yetasimplelyicsapp.domain.lyrics.TimingQuality

class LyricsEntityMapperTest {
    @Test
    fun normalizedGraphRoundTripsRichLyricsAndMetadata() {
        val original = LyricsDocument(
            id = "document-1",
            trackKey = "track-1",
            source = LyricsSource("local", attribution = "Imported"),
            syncType = SyncType.WORD,
            lines = listOf(
                LyricsLine(
                    id = "line-1",
                    order = 0,
                    text = LocalizedText(
                        original = "Hola",
                        translations = mapOf("en" to "Hello"),
                        romanizations = mapOf("Latn" to "Hola"),
                    ),
                    startTimeMs = 1_000L,
                    endTimeMs = 2_000L,
                    words = listOf(
                        LyricsWord(
                            id = "word-1",
                            text = "Hola",
                            startTimeMs = 1_000L,
                            endTimeMs = 2_000L,
                            timingQuality = TimingQuality.EXPLICIT,
                            segments = listOf(
                                LyricsWordSegment(
                                    text = "Ho",
                                    startTimeMs = 1_000L,
                                    endTimeMs = 1_400L,
                                    timingQuality = TimingQuality.EXPLICIT,
                                ),
                                LyricsWordSegment(
                                    text = "la",
                                    startTimeMs = 1_400L,
                                    endTimeMs = 2_000L,
                                    timingQuality = TimingQuality.EXPLICIT,
                                ),
                            ),
                        ),
                    ),
                    agentId = "v1",
                    groupId = "duet-1",
                    alignment = LyricsAlignment.RIGHT,
                    role = "lead",
                    region = "speaker-right",
                    direction = LyricsTextDirection.RTL,
                    timingQuality = TimingQuality.EXPLICIT,
                    isBackground = true,
                ),
            ),
            languageTag = "es",
            capabilities = setOf(
                LyricsCapability.PLAIN_TEXT,
                LyricsCapability.WORD_SYNC,
                LyricsCapability.TRANSLATION,
            ),
        )
        val graph = LyricsEntityMapper.toStored(
            document = original,
            cachedAtEpochMs = 10L,
            expiresAtEpochMs = 20L,
            metadata = LyricsTrackMetadata(
                title = "Canción",
                artists = listOf("Artista"),
                album = "Álbum",
                durationMs = 3_000L,
                artworkUri = "file:///data/user/0/example/files/artwork/cover.webp",
            ),
            isPinned = true,
            lastAccessedAtEpochMs = 15L,
        )

        assertEquals(original, LyricsEntityMapper.toDomain(graph))
        assertEquals(listOf("Ho", "la"), graph.wordSegments.map { it.text })
        assertEquals("Canción", graph.document.title)
        assertEquals("Artista", graph.document.artists)
        assertEquals(
            "file:///data/user/0/example/files/artwork/cover.webp",
            graph.document.artworkUri,
        )
        assertTrue(graph.document.isPinned)
    }

    @Test
    fun legacySnapWordsAreReconstructedWhenLoadingTheStoredGraph() {
        val base = LyricsDocument(
            id = "legacy-document",
            trackKey = "legacy-track",
            source = LyricsSource("local"),
            syncType = SyncType.WORD,
            lines = listOf(
                LyricsLine(
                    id = "line-1",
                    order = 0,
                    text = LocalizedText("quiere te quiero"),
                    startTimeMs = 1_000L,
                    endTimeMs = 3_000L,
                    words = listOf(
                        word("snap-0", "qui", 1_000L, 1_200L),
                        word("snap-1", "e", 1_200L, 1_400L),
                        word("snap-2", "re ", 1_400L, 1_600L),
                        word("snap-3", "te ", 1_600L, 2_000L),
                        word("snap-4", "quiero", 2_000L, 3_000L),
                    ),
                ),
            ),
        )
        val stored = LyricsEntityMapper.toStored(base, cachedAtEpochMs = 0L, expiresAtEpochMs = 1L)

        val restored = LyricsEntityMapper.toDomain(stored)

        val words = restored.lines.single().words
        assertEquals(listOf("quiere", "te", "quiero"), words.map { it.text })
        assertEquals(listOf("qui", "e", "re "), words.first().segments.map { it.text })
        assertEquals(1_000L, words.first().startTimeMs)
        assertEquals(1_600L, words.first().endTimeMs)
    }

    private fun word(id: String, text: String, start: Long, end: Long) = LyricsWord(
        id = id,
        text = text,
        startTimeMs = start,
        endTimeMs = end,
        timingQuality = TimingQuality.EXPLICIT,
    )
}
