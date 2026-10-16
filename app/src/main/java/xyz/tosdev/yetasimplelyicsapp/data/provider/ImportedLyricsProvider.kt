package xyz.tosdev.yetasimplelyicsapp.data.provider

import java.security.MessageDigest
import xyz.tosdev.yetasimplelyicsapp.data.local.LyricsTrackMetadata
import xyz.tosdev.yetasimplelyicsapp.data.local.RoomLyricsStore
import xyz.tosdev.yetasimplelyicsapp.domain.lyrics.LyricsCapability
import xyz.tosdev.yetasimplelyicsapp.domain.lyrics.LyricsDocument
import xyz.tosdev.yetasimplelyicsapp.domain.lyrics.LyricsProvider
import xyz.tosdev.yetasimplelyicsapp.domain.lyrics.LyricsProviderDescriptor
import xyz.tosdev.yetasimplelyicsapp.domain.lyrics.LyricsProviderResult
import xyz.tosdev.yetasimplelyicsapp.domain.lyrics.LyricsRequest
import xyz.tosdev.yetasimplelyicsapp.domain.lyrics.LyricsSource
import xyz.tosdev.yetasimplelyicsapp.domain.lyrics.parser.LrcLyricsParser
import xyz.tosdev.yetasimplelyicsapp.domain.lyrics.parser.LyricsParseContext
import xyz.tosdev.yetasimplelyicsapp.domain.lyrics.parser.LyricsTextParser
import xyz.tosdev.yetasimplelyicsapp.domain.lyrics.parser.TtmlLyricsParser

enum class ImportedLyricsFormat {
    PLAIN_TEXT,
    LRC,
    TTML,
}

class ImportedLyricsProvider(
    private val store: RoomLyricsStore,
    private val nowEpochMs: () -> Long = System::currentTimeMillis,
) : LyricsProvider {
    override val descriptor = LyricsProviderDescriptor(
        id = "local",
        displayName = "Imported lyrics",
        capabilities = setOf(
            LyricsCapability.PLAIN_TEXT,
            LyricsCapability.LINE_SYNC,
            LyricsCapability.WORD_SYNC,
            LyricsCapability.TRANSLATION,
            LyricsCapability.ROMANIZATION,
            LyricsCapability.MULTI_AGENT,
        ),
        requiresNetwork = false,
    )

    override suspend fun findLyrics(request: LyricsRequest): LyricsProviderResult {
        val cached = store.find(request.trackKey)
        return if (cached?.document?.source?.providerId == descriptor.id) {
            LyricsProviderResult.Found(cached.document, confidence = 1.0)
        } else {
            LyricsProviderResult.NotFound("No imported lyrics for this track")
        }
    }

    suspend fun import(
        request: LyricsRequest,
        rawLyrics: String,
        format: ImportedLyricsFormat,
    ): LyricsDocument {
        require(rawLyrics.isNotBlank()) { "rawLyrics must not be blank" }
        val source = LyricsSource(
            providerId = descriptor.id,
            attribution = "Imported on this device",
        )
        val context = LyricsParseContext(
            documentId = "local:${contentId(request.trackKey, rawLyrics)}",
            trackKey = request.trackKey,
            source = source,
        )
        val parser: LyricsTextParser = when (format) {
            ImportedLyricsFormat.PLAIN_TEXT,
            ImportedLyricsFormat.LRC,
            -> LrcLyricsParser()
            ImportedLyricsFormat.TTML -> TtmlLyricsParser()
        }
        val parseResult = parser.parse(rawLyrics, context)
        val document = requireNotNull(parseResult.document) {
            parseResult.diagnostics.joinToString { it.message }
                .ifBlank { "The imported document contains no lyric lines" }
        }
        val now = nowEpochMs()
        store.save(
            document = document,
            cachedAtEpochMs = now,
            expiresAtEpochMs = Long.MAX_VALUE,
            metadata = LyricsTrackMetadata(
                title = request.title,
                artists = request.artists,
                album = request.album,
                durationMs = request.durationMs,
                artworkUri = request.artworkUri,
            ),
        )
        return document
    }

    private fun contentId(trackKey: String, rawLyrics: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest((trackKey + '\u0000' + rawLyrics).toByteArray(Charsets.UTF_8))
            .take(12)
            .joinToString("") { byte -> "%02x".format(byte) }
}
