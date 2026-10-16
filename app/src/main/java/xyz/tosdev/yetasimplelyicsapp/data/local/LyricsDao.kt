package xyz.tosdev.yetasimplelyicsapp.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
abstract class LyricsDao {
    @Query("SELECT * FROM lyrics_documents WHERE trackKey = :trackKey LIMIT 1")
    abstract suspend fun findDocument(trackKey: String): LyricsDocumentEntity?

    @Query("SELECT * FROM lyrics_lines WHERE documentId = :documentId ORDER BY position")
    abstract suspend fun findLines(documentId: String): List<LyricsLineEntity>

    @Query(
        "SELECT * FROM lyrics_words WHERE documentId = :documentId " +
            "ORDER BY lineId, position",
    )
    abstract suspend fun findWords(documentId: String): List<LyricsWordEntity>

    @Query(
        "SELECT * FROM lyrics_word_segments WHERE documentId = :documentId " +
            "ORDER BY lineId, wordId, position",
    )
    abstract suspend fun findWordSegments(documentId: String): List<LyricsWordSegmentEntity>

    @Query(
        "SELECT * FROM lyrics_text_variants WHERE documentId = :documentId " +
            "ORDER BY lineId, kind, languageTag",
    )
    abstract suspend fun findVariants(documentId: String): List<LyricsTextVariantEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertDocument(document: LyricsDocumentEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertLines(lines: List<LyricsLineEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertWords(words: List<LyricsWordEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertWordSegments(segments: List<LyricsWordSegmentEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertVariants(variants: List<LyricsTextVariantEntity>)

    @Query("DELETE FROM lyrics_documents WHERE trackKey = :trackKey")
    abstract suspend fun deleteByTrackKey(trackKey: String)

    @Query("DELETE FROM lyrics_documents WHERE expiresAtEpochMs < :nowEpochMs AND isPinned = 0")
    abstract suspend fun deleteExpired(nowEpochMs: Long): Int

    @Transaction
    open suspend fun loadByTrackKey(trackKey: String): StoredLyricsGraph? {
        val document = findDocument(trackKey) ?: return null
        return StoredLyricsGraph(
            document = document,
            lines = findLines(document.documentId),
            words = findWords(document.documentId),
            wordSegments = findWordSegments(document.documentId),
            variants = findVariants(document.documentId),
        )
    }

    @Transaction
    open suspend fun replace(graph: StoredLyricsGraph) {
        deleteByTrackKey(graph.document.trackKey)
        insertDocument(graph.document)
        if (graph.lines.isNotEmpty()) insertLines(graph.lines)
        if (graph.words.isNotEmpty()) insertWords(graph.words)
        if (graph.wordSegments.isNotEmpty()) insertWordSegments(graph.wordSegments)
        if (graph.variants.isNotEmpty()) insertVariants(graph.variants)
    }

    @Query(
        "SELECT * FROM lyrics_documents " +
                "ORDER BY isPinned DESC, lastAccessedAtEpochMs DESC, cachedAtEpochMs DESC",
    )
    abstract fun observeLibrary(): kotlinx.coroutines.flow.Flow<List<LyricsDocumentEntity>>

    @Query("SELECT * FROM lyrics_documents WHERE documentId = :documentId LIMIT 1")
    abstract suspend fun findDocumentById(documentId: String): LyricsDocumentEntity?

    @Query("UPDATE lyrics_documents SET isPinned = :pinned WHERE documentId = :documentId")
    abstract suspend fun setPinned(documentId: String, pinned: Boolean): Int

    @Query(
        "UPDATE lyrics_documents SET lastAccessedAtEpochMs = :accessedAtEpochMs " +
                "WHERE documentId = :documentId",
    )
    abstract suspend fun touch(documentId: String, accessedAtEpochMs: Long): Int

    @Query("UPDATE lyrics_documents SET artworkUri = :artworkUri WHERE trackKey = :trackKey")
    abstract suspend fun updateArtwork(trackKey: String, artworkUri: String): Int

    @Transaction
    open suspend fun loadById(documentId: String): StoredLyricsGraph? {
        val document = findDocumentById(documentId) ?: return null
        return StoredLyricsGraph(
            document = document,
            lines = findLines(document.documentId),
            words = findWords(document.documentId),
            wordSegments = findWordSegments(document.documentId),
            variants = findVariants(document.documentId),
        )
    }
}
