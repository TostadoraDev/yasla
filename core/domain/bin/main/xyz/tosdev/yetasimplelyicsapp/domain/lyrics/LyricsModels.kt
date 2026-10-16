package xyz.tosdev.yetasimplelyicsapp.domain.lyrics

enum class SyncType {
    PLAIN,
    LINE,
    WORD,
    MIXED,
}

enum class TimingQuality {
    EXPLICIT,
    INFERRED,
    UNKNOWN,
}

enum class LyricsCapability {
    PLAIN_TEXT,
    LINE_SYNC,
    WORD_SYNC,
    TRANSLATION,
    ROMANIZATION,
    MULTI_AGENT,
}

data class LyricsSource(
    val providerId: String,
    val remoteId: String? = null,
    val attribution: String? = null,
    val contentRights: String? = null,
) {
    init {
        require(providerId.isNotBlank()) { "providerId must not be blank" }
    }
}

data class LocalizedText(
    val original: String,
    val translations: Map<String, String> = emptyMap(),
    val romanizations: Map<String, String> = emptyMap(),
)

data class LyricsWord(
    val id: String,
    val text: String,
    val startTimeMs: Long? = null,
    val endTimeMs: Long? = null,
    val timingQuality: TimingQuality = TimingQuality.UNKNOWN,
) {
    init {
        require(id.isNotBlank()) { "word id must not be blank" }
        require(startTimeMs == null || startTimeMs >= 0L) { "word startTimeMs must be non-negative" }
        require(endTimeMs == null || endTimeMs >= 0L) { "word endTimeMs must be non-negative" }
        require(startTimeMs == null || endTimeMs == null || endTimeMs >= startTimeMs) {
            "word endTimeMs must be greater than or equal to startTimeMs"
        }
    }
}

data class LyricsLine(
    val id: String,
    val order: Int,
    val text: LocalizedText,
    val startTimeMs: Long? = null,
    val endTimeMs: Long? = null,
    val words: List<LyricsWord> = emptyList(),
    val agentId: String? = null,
    val groupId: String? = null,
    val timingQuality: TimingQuality = TimingQuality.UNKNOWN,
) {
    init {
        require(id.isNotBlank()) { "line id must not be blank" }
        require(order >= 0) { "line order must be non-negative" }
        require(startTimeMs == null || startTimeMs >= 0L) { "line startTimeMs must be non-negative" }
        require(endTimeMs == null || endTimeMs >= 0L) { "line endTimeMs must be non-negative" }
        require(startTimeMs == null || endTimeMs == null || endTimeMs >= startTimeMs) {
            "line endTimeMs must be greater than or equal to startTimeMs"
        }
    }
}

data class LyricsDocument(
    val id: String,
    val trackKey: String,
    val source: LyricsSource,
    val syncType: SyncType,
    val lines: List<LyricsLine>,
    val languageTag: String? = null,
    val capabilities: Set<LyricsCapability> = emptySet(),
    val schemaVersion: Int = 1,
) {
    init {
        require(id.isNotBlank()) { "document id must not be blank" }
        require(trackKey.isNotBlank()) { "trackKey must not be blank" }
        require(schemaVersion > 0) { "schemaVersion must be positive" }
        require(lines.map(LyricsLine::id).distinct().size == lines.size) {
            "line ids must be unique inside a document"
        }
    }
}
