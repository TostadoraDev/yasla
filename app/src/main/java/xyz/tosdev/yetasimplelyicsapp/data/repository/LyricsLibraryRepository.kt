package xyz.tosdev.yetasimplelyicsapp.data.repository

import kotlinx.coroutines.flow.Flow
import xyz.tosdev.yetasimplelyicsapp.data.local.CachedLyrics
import xyz.tosdev.yetasimplelyicsapp.data.local.LyricsLibraryItem
import xyz.tosdev.yetasimplelyicsapp.data.local.RoomLyricsStore
import xyz.tosdev.yetasimplelyicsapp.data.provider.ImportedLyricsFormat
import xyz.tosdev.yetasimplelyicsapp.data.provider.ImportedLyricsProvider
import xyz.tosdev.yetasimplelyicsapp.domain.lyrics.LyricsDocument
import xyz.tosdev.yetasimplelyicsapp.domain.lyrics.LyricsRequest
import xyz.tosdev.yetasimplelyicsapp.domain.lyrics.parser.LyricsDocumentExporter
import xyz.tosdev.yetasimplelyicsapp.domain.lyrics.parser.LyricsExportFormat
import xyz.tosdev.yetasimplelyicsapp.domain.lyrics.parser.LyricsExportResult

class LyricsLibraryRepository(
    private val store: RoomLyricsStore,
    private val importedProvider: ImportedLyricsProvider,
    private val exporter: LyricsDocumentExporter = LyricsDocumentExporter(),
    private val nowEpochMs: () -> Long = System::currentTimeMillis,
) {
    fun observeLibrary(): Flow<List<LyricsLibraryItem>> = store.observeLibrary()

    suspend fun getDocument(documentId: String): CachedLyrics? {
        val result = store.findById(documentId) ?: return null
        store.markAccessed(documentId, nowEpochMs())
        return result
    }

    suspend fun setPinned(documentId: String, pinned: Boolean): Boolean =
        store.setPinned(documentId, pinned)

    suspend fun import(
        request: LyricsRequest,
        content: String,
        format: ImportedLyricsFormat,
    ): LyricsDocument = importedProvider.import(request, content, format)

    suspend fun export(
        documentId: String,
        format: LyricsExportFormat,
    ): LyricsExportResult? {
        val cached = getDocument(documentId) ?: return null
        return exporter.export(cached.document, format)
    }
}
