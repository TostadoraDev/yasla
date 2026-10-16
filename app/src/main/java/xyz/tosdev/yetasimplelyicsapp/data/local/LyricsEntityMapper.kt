package xyz.tosdev.yetasimplelyicsapp.data.local

import xyz.tosdev.yetasimplelyicsapp.domain.lyrics.LocalizedText
import xyz.tosdev.yetasimplelyicsapp.domain.lyrics.LyricsCapability
import xyz.tosdev.yetasimplelyicsapp.domain.lyrics.LyricsAlignment
import xyz.tosdev.yetasimplelyicsapp.domain.lyrics.LyricsDocument
import xyz.tosdev.yetasimplelyicsapp.domain.lyrics.LyricsLine
import xyz.tosdev.yetasimplelyicsapp.domain.lyrics.LyricsSource
import xyz.tosdev.yetasimplelyicsapp.domain.lyrics.LyricsTextDirection
import xyz.tosdev.yetasimplelyicsapp.domain.lyrics.LyricsWord
import xyz.tosdev.yetasimplelyicsapp.domain.lyrics.LyricsWordSegment
import xyz.tosdev.yetasimplelyicsapp.domain.lyrics.SyncType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import xyz.tosdev.yetasimplelyicsapp.domain.lyrics.TimingQuality
import xyz.tosdev.yetasimplelyicsapp.domain.lyrics.reconstructSemanticLyricsWords

internal object LyricsEntityMapper {
    private const val TRANSLATION = "translation"
    private const val ROMANIZATION = "romanization"
    private const val ARTIST_SEPARATOR = "\u001F"

    fun toStored(
        document: LyricsDocument,
        cachedAtEpochMs: Long,
        expiresAtEpochMs: Long,
        metadata: LyricsTrackMetadata = LyricsTrackMetadata(),
        isPinned: Boolean = false,
        lastAccessedAtEpochMs: Long = cachedAtEpochMs,
    ): StoredLyricsGraph {
        val documentEntity = LyricsDocumentEntity(
            documentId = document.id,
            trackKey = document.trackKey,
            providerId = document.source.providerId,
            remoteId = document.source.remoteId,
            attribution = document.source.attribution,
            contentRights = document.source.contentRights,
            syncType = document.syncType.name,
            languageTag = document.languageTag,
            capabilities = document.capabilities
                .map(LyricsCapability::name)
                .sorted()
                .joinToString(","),
            schemaVersion = document.schemaVersion,
            cachedAtEpochMs = cachedAtEpochMs,
            expiresAtEpochMs = expiresAtEpochMs,
            title = metadata.title,
            artists = metadata.artists.joinToString(ARTIST_SEPARATOR),
            album = metadata.album,
            durationMs = metadata.durationMs,
            artworkUri = metadata.artworkUri,
            isPinned = isPinned,
            lastAccessedAtEpochMs = lastAccessedAtEpochMs,
        )
        val lines = document.lines.map { line ->
            LyricsLineEntity(
                documentId = document.id,
                lineId = line.id,
                position = line.order,
                originalText = line.text.original,
                startTimeMs = line.startTimeMs,
                endTimeMs = line.endTimeMs,
                agentId = line.agentId,
                groupId = line.groupId,
                alignment = line.alignment.name,
                role = line.role,
                region = line.region,
                textDirection = line.direction.name,
                timingQuality = line.timingQuality.name,
                isBackground = line.isBackground,
            )
        }
        val words = document.lines.flatMap { line ->
            line.words.mapIndexed { index, word ->
                LyricsWordEntity(
                    documentId = document.id,
                    lineId = line.id,
                    wordId = word.id,
                    position = index,
                    text = word.text,
                    startTimeMs = word.startTimeMs,
                    endTimeMs = word.endTimeMs,
                    timingQuality = word.timingQuality.name,
                )
            }
        }
        val wordSegments = document.lines.flatMap { line ->
            line.words.flatMap { word ->
                word.segments.mapIndexed { index, segment ->
                    LyricsWordSegmentEntity(
                        documentId = document.id,
                        lineId = line.id,
                        wordId = word.id,
                        position = index,
                        text = segment.text,
                        startTimeMs = segment.startTimeMs,
                        endTimeMs = segment.endTimeMs,
                        timingQuality = segment.timingQuality.name,
                    )
                }
            }
        }
        val variants = document.lines.flatMap { line ->
            buildList {
                line.text.translations.forEach { (languageTag, text) ->
                    add(
                        LyricsTextVariantEntity(
                            documentId = document.id,
                            lineId = line.id,
                            kind = TRANSLATION,
                            languageTag = languageTag,
                            text = text,
                        ),
                    )
                }
                line.text.romanizations.forEach { (languageTag, text) ->
                    add(
                        LyricsTextVariantEntity(
                            documentId = document.id,
                            lineId = line.id,
                            kind = ROMANIZATION,
                            languageTag = languageTag,
                            text = text,
                        ),
                    )
                }
            }
        }
        return StoredLyricsGraph(
            document = documentEntity,
            lines = lines,
            words = words,
            wordSegments = wordSegments,
            variants = variants,
        )
    }

    fun toDomain(graph: StoredLyricsGraph): LyricsDocument {
        val wordsByLine = graph.words.groupBy(LyricsWordEntity::lineId)
        val segmentsByWord = graph.wordSegments.groupBy { segment ->
            segment.lineId to segment.wordId
        }
        val variantsByLine = graph.variants.groupBy(LyricsTextVariantEntity::lineId)
        return LyricsDocument(
            id = graph.document.documentId,
            trackKey = graph.document.trackKey,
            source = LyricsSource(
                providerId = graph.document.providerId,
                remoteId = graph.document.remoteId,
                attribution = graph.document.attribution,
                contentRights = graph.document.contentRights,
            ),
            syncType = enumValueOrDefault(graph.document.syncType, SyncType.PLAIN),
            lines = graph.lines.sortedBy(LyricsLineEntity::position).map { line ->
                val variants = variantsByLine[line.lineId].orEmpty()
                val persistedWords = wordsByLine[line.lineId].orEmpty()
                    .sortedBy(LyricsWordEntity::position)
                    .map { word ->
                        LyricsWord(
                            id = word.wordId,
                            text = word.text,
                            startTimeMs = word.startTimeMs,
                            endTimeMs = word.endTimeMs,
                            timingQuality = enumValueOrDefault(
                                word.timingQuality,
                                TimingQuality.UNKNOWN,
                            ),
                            segments = segmentsByWord[line.lineId to word.wordId].orEmpty()
                                .sortedBy(LyricsWordSegmentEntity::position)
                                .map { segment ->
                                    LyricsWordSegment(
                                        text = segment.text,
                                        startTimeMs = segment.startTimeMs,
                                        endTimeMs = segment.endTimeMs,
                                        timingQuality = enumValueOrDefault(
                                            segment.timingQuality,
                                            TimingQuality.UNKNOWN,
                                        ),
                                    )
                                },
                        )
                    }
                LyricsLine(
                    id = line.lineId,
                    order = line.position,
                    text = LocalizedText(
                        original = line.originalText,
                        translations = variants
                            .filter { it.kind == TRANSLATION }
                            .associate { it.languageTag to it.text },
                        romanizations = variants
                            .filter { it.kind == ROMANIZATION }
                            .associate { it.languageTag to it.text },
                    ),
                    startTimeMs = line.startTimeMs,
                    endTimeMs = line.endTimeMs,
                    words = reconstructSemanticLyricsWords(
                        lineText = line.originalText,
                        words = persistedWords,
                        languageTag = graph.document.languageTag,
                    ),
                    agentId = line.agentId,
                    groupId = line.groupId,
                    alignment = enumValueOrDefault(line.alignment, LyricsAlignment.AUTO),
                    role = line.role,
                    region = line.region,
                    direction = enumValueOrDefault(line.textDirection, LyricsTextDirection.AUTO),
                    timingQuality = enumValueOrDefault(
                        line.timingQuality,
                        TimingQuality.UNKNOWN,
                    ),
                    isBackground = line.isBackground,
                )
            },
            languageTag = graph.document.languageTag,
            capabilities = graph.document.capabilities
                .split(',')
                .filter(String::isNotBlank)
                .mapNotNull { runCatching { LyricsCapability.valueOf(it) }.getOrNull() }
                .toSet(),
            schemaVersion = graph.document.schemaVersion,
        )
    }

    private inline fun <reified T : Enum<T>> enumValueOrDefault(value: String, fallback: T): T =
        runCatching { enumValueOf<T>(value) }.getOrDefault(fallback)
}

data class LyricsTrackMetadata(
    val title: String? = null,
    val artists: List<String> = emptyList(),
    val album: String? = null,
    val durationMs: Long? = null,
    val artworkUri: String? = null,
)

data class CachedLyrics(
    val document: LyricsDocument,
    val cachedAtEpochMs: Long,
    val expiresAtEpochMs: Long,
    val metadata: LyricsTrackMetadata,
    val isPinned: Boolean,
    val lastAccessedAtEpochMs: Long,
)

data class LyricsLibraryItem(
    val documentId: String,
    val trackKey: String,
    val sourceId: String,
    val syncType: SyncType,
    val metadata: LyricsTrackMetadata,
    val isPinned: Boolean,
    val cachedAtEpochMs: Long,
    val lastAccessedAtEpochMs: Long,
)

class RoomLyricsStore(private val dao: LyricsDao) {
    fun observeLibrary(): Flow<List<LyricsLibraryItem>> =
        dao.observeLibrary().map { documents -> documents.map { it.toLibraryItem() } }

    suspend fun find(trackKey: String): CachedLyrics? =
        dao.loadByTrackKey(trackKey)?.toCachedLyrics()

    suspend fun findById(documentId: String): CachedLyrics? =
        dao.loadById(documentId)?.toCachedLyrics()

    suspend fun save(
        document: LyricsDocument,
        cachedAtEpochMs: Long,
        expiresAtEpochMs: Long,
        metadata: LyricsTrackMetadata = LyricsTrackMetadata(),
    ) {
        val existing = dao.findDocument(document.trackKey)
        val effectiveMetadata = metadata.withFallback(existing?.toMetadata())
        dao.replace(
            LyricsEntityMapper.toStored(
                document = document,
                cachedAtEpochMs = cachedAtEpochMs,
                expiresAtEpochMs = expiresAtEpochMs,
                metadata = effectiveMetadata,
                isPinned = existing?.isPinned ?: false,
                lastAccessedAtEpochMs = cachedAtEpochMs,
            ),
        )
    }

    suspend fun setPinned(documentId: String, pinned: Boolean): Boolean =
        dao.setPinned(documentId, pinned) > 0

    suspend fun markAccessed(documentId: String, accessedAtEpochMs: Long): Boolean =
        dao.touch(documentId, accessedAtEpochMs) > 0

    suspend fun deleteExpired(nowEpochMs: Long): Int = dao.deleteExpired(nowEpochMs)

    suspend fun updateArtwork(trackKey: String, artworkUri: String): Boolean =
        dao.updateArtwork(trackKey, artworkUri) > 0

    private fun StoredLyricsGraph.toCachedLyrics(): CachedLyrics = CachedLyrics(
        document = LyricsEntityMapper.toDomain(this),
        cachedAtEpochMs = document.cachedAtEpochMs,
        expiresAtEpochMs = document.expiresAtEpochMs,
        metadata = document.toMetadata(),
        isPinned = document.isPinned,
        lastAccessedAtEpochMs = document.lastAccessedAtEpochMs,
    )

    private fun LyricsDocumentEntity.toLibraryItem() = LyricsLibraryItem(
        documentId = documentId,
        trackKey = trackKey,
        sourceId = providerId,
        syncType = runCatching { SyncType.valueOf(syncType) }.getOrDefault(SyncType.PLAIN),
        metadata = toMetadata(),
        isPinned = isPinned,
        cachedAtEpochMs = cachedAtEpochMs,
        lastAccessedAtEpochMs = lastAccessedAtEpochMs,
    )

    private fun LyricsDocumentEntity.toMetadata() = LyricsTrackMetadata(
        title = title,
        artists = artists.split("\u001F").filter(String::isNotBlank),
        album = album,
        durationMs = durationMs,
        artworkUri = artworkUri,
    )

    private fun LyricsTrackMetadata.withFallback(fallback: LyricsTrackMetadata?) =
        LyricsTrackMetadata(
            title = title ?: fallback?.title,
            artists = artists.ifEmpty { fallback?.artists.orEmpty() },
            album = album ?: fallback?.album,
            durationMs = durationMs ?: fallback?.durationMs,
            artworkUri = artworkUri ?: fallback?.artworkUri,
        )
}
