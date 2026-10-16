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

enum class LyricsAlignment {
    LEFT,
    RIGHT,
    CENTER,
    AUTO,
}

enum class LyricsTextDirection {
    LTR,
    RTL,
    AUTO,
}

enum class LyricsCapability {
    PLAIN_TEXT,
    LINE_SYNC,
    WORD_SYNC,
    TRANSLATION,
    ROMANIZATION,
    MULTI_AGENT,
    /** Apple lyric timing metadata recognized by the parser, not inferred from .ttml. */
    AMLL_TIMING,
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

data class LyricsWordSegment(
    val text: String,
    val startTimeMs: Long? = null,
    val endTimeMs: Long? = null,
    val timingQuality: TimingQuality = TimingQuality.UNKNOWN,
) {
    init {
        require(startTimeMs == null || startTimeMs >= 0L) { "segment startTimeMs must be non-negative" }
        require(endTimeMs == null || endTimeMs >= 0L) { "segment endTimeMs must be non-negative" }
        require(startTimeMs == null || endTimeMs == null || endTimeMs >= startTimeMs) {
            "segment endTimeMs must be greater than or equal to startTimeMs"
        }
    }
}

data class LyricsWord(
    val id: String,
    val text: String,
    val startTimeMs: Long? = null,
    val endTimeMs: Long? = null,
    val timingQuality: TimingQuality = TimingQuality.UNKNOWN,
    val segments: List<LyricsWordSegment> = emptyList(),
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
    val alignment: LyricsAlignment = LyricsAlignment.AUTO,
    val role: String? = null,
    val region: String? = null,
    val direction: LyricsTextDirection = LyricsTextDirection.AUTO,
    val timingQuality: TimingQuality = TimingQuality.UNKNOWN,
    val isBackground: Boolean = false,
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

/**
 * True only when the document contains enough complete, valid semantic word timing to drive a
 * current-word surface. The file format and advertised capabilities are deliberately ignored:
 * line-timed TTML is still line timed.
 */
val LyricsDocument.hasReliableWordSync: Boolean
    get() {
        val timedForegroundLines = lines.filter { line ->
            !line.isBackground &&
                line.text.original.normalizedLexicalText().isNotEmpty() &&
                line.startTimeMs != null
        }
        if (timedForegroundLines.isEmpty()) return false

        val reliableLines = timedForegroundLines.count(LyricsLine::hasReliableWordTiming)
        return reliableLines > 0 &&
            reliableLines.toDouble() / timedForegroundLines.size.toDouble() >= 0.6
    }

/**
 * Format-independent capability check for a semantic word timeline. A final open-ended word is
 * valid because the sync engine can close it with the line, next line, or media duration.
 */
val LyricsLine.hasReliableWordTiming: Boolean
    get() {
        val semanticWords = words.filter { it.text.normalizedLexicalText().isNotEmpty() }
        if (semanticWords.isEmpty()) return false
        if (startTimeMs == null) return false
        if (semanticWords.any { word ->
                word.text.trim().any(Char::isWhitespace) ||
                    word.startTimeMs == null ||
                    !word.hasValidSegmentTiming()
            }
        ) {
            return false
        }

        semanticWords.forEachIndexed { index, word ->
            val start = word.startTimeMs ?: return false
            val effectiveEnd = word.endTimeMs
                ?: semanticWords.getOrNull(index + 1)?.startTimeMs
                ?: endTimeMs
            if (effectiveEnd != null && effectiveEnd <= start) return false
            if (index < semanticWords.lastIndex && effectiveEnd == null) return false
        }

        val lineText = text.original.normalizedLexicalText()
        val reconstructedText = semanticWords.joinToString(separator = "") { it.text }
            .normalizedLexicalText()
        return reconstructedText == lineText
    }

private fun LyricsWord.hasValidSegmentTiming(): Boolean {
    val meaningfulSegments = segments.filter { it.text.isNotBlank() }
    return meaningfulSegments.isEmpty() || meaningfulSegments.all(LyricsWordSegment::hasValidInterval)
}

private fun LyricsWordSegment.hasValidInterval(): Boolean =
    startTimeMs != null && endTimeMs != null && endTimeMs > startTimeMs

private fun String.normalizedLexicalText(): String =
    filter(Char::isLetterOrDigit).lowercase()
