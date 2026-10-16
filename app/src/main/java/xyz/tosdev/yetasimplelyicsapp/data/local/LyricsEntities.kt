package xyz.tosdev.yetasimplelyicsapp.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "lyrics_documents",
    indices = [
        Index(value = ["trackKey"], unique = true),
        Index(value = ["expiresAtEpochMs"]),
        Index(value = ["isPinned"]),
        Index(value = ["lastAccessedAtEpochMs"]),
    ],
)
data class LyricsDocumentEntity(
    @androidx.room.PrimaryKey val documentId: String,
    val trackKey: String,
    val providerId: String,
    val remoteId: String?,
    val attribution: String?,
    val contentRights: String?,
    val syncType: String,
    val languageTag: String?,
    val capabilities: String,
    val schemaVersion: Int,
    val cachedAtEpochMs: Long,
    val expiresAtEpochMs: Long,
    val title: String?,
    val artists: String,
    val album: String?,
    val durationMs: Long?,
    val artworkUri: String?,
    val isPinned: Boolean,
    val lastAccessedAtEpochMs: Long,
)

@Entity(
    tableName = "lyrics_lines",
    primaryKeys = ["documentId", "lineId"],
    foreignKeys = [
        ForeignKey(
            entity = LyricsDocumentEntity::class,
            parentColumns = ["documentId"],
            childColumns = ["documentId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["documentId"])],
)
data class LyricsLineEntity(
    val documentId: String,
    val lineId: String,
    val position: Int,
    val originalText: String,
    val startTimeMs: Long?,
    val endTimeMs: Long?,
    val agentId: String?,
    val groupId: String?,
    @ColumnInfo(defaultValue = "'AUTO'")
    val alignment: String,
    val role: String?,
    val region: String?,
    @ColumnInfo(defaultValue = "'AUTO'")
    val textDirection: String,
    val timingQuality: String,
    @ColumnInfo(defaultValue = "0")
    val isBackground: Boolean,
)

@Entity(
    tableName = "lyrics_words",
    primaryKeys = ["documentId", "lineId", "wordId"],
    foreignKeys = [
        ForeignKey(
            entity = LyricsLineEntity::class,
            parentColumns = ["documentId", "lineId"],
            childColumns = ["documentId", "lineId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["documentId", "lineId"])],
)
data class LyricsWordEntity(
    val documentId: String,
    val lineId: String,
    val wordId: String,
    val position: Int,
    val text: String,
    val startTimeMs: Long?,
    val endTimeMs: Long?,
    val timingQuality: String,
)

@Entity(
    tableName = "lyrics_word_segments",
    primaryKeys = ["documentId", "lineId", "wordId", "position"],
    foreignKeys = [
        ForeignKey(
            entity = LyricsWordEntity::class,
            parentColumns = ["documentId", "lineId", "wordId"],
            childColumns = ["documentId", "lineId", "wordId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["documentId", "lineId", "wordId"])],
)
data class LyricsWordSegmentEntity(
    val documentId: String,
    val lineId: String,
    val wordId: String,
    val position: Int,
    val text: String,
    val startTimeMs: Long?,
    val endTimeMs: Long?,
    val timingQuality: String,
)

@Entity(
    tableName = "lyrics_text_variants",
    primaryKeys = ["documentId", "lineId", "kind", "languageTag"],
    foreignKeys = [
        ForeignKey(
            entity = LyricsLineEntity::class,
            parentColumns = ["documentId", "lineId"],
            childColumns = ["documentId", "lineId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["documentId", "lineId"])],
)
data class LyricsTextVariantEntity(
    val documentId: String,
    val lineId: String,
    val kind: String,
    val languageTag: String,
    val text: String,
)

data class StoredLyricsGraph(
    val document: LyricsDocumentEntity,
    val lines: List<LyricsLineEntity>,
    val words: List<LyricsWordEntity>,
    val wordSegments: List<LyricsWordSegmentEntity>,
    val variants: List<LyricsTextVariantEntity>,
)
