package xyz.tosdev.yetasimplelyicsapp.domain.lyrics

import java.text.BreakIterator
import java.util.Locale

/**
 * Repairs legacy timelines that persisted one [LyricsWord] per timed snap.
 *
 * Timing fragments are projected onto lexical tokens from the actual line text. Span boundaries
 * never create word boundaries by themselves. Already-normalized words are returned untouched so
 * their original segment text (including separator whitespace) remains byte-for-byte stable.
 */
fun reconstructSemanticLyricsWords(
    lineText: String,
    words: List<LyricsWord>,
    languageTag: String? = null,
): List<LyricsWord> {
    if (words.isEmpty()) return words
    val tokens = semanticTextTokens(lineText, languageTag)
    if (tokens.isEmpty()) return words
    if (words.map { it.text.trim() } == tokens.map(SemanticTextToken::text)) {
        return words.zip(tokens) { word, token ->
            if (word.text == token.text) word else word.copy(text = token.text)
        }
    }

    val sources = words.flatMap { word ->
        val segments = word.segments.ifEmpty {
            listOf(
                LyricsWordSegment(
                    text = word.text,
                    startTimeMs = word.startTimeMs,
                    endTimeMs = word.endTimeMs,
                    timingQuality = word.timingQuality,
                ),
            )
        }
        segments.map { segment -> SourceSegment(word.id, segment) }
    }.filter { source -> source.segment.text.any { !it.isWhitespace() } }
    if (sources.isEmpty()) return words

    val compactLine = tokens.joinToString(separator = "") { it.text.filterNot(Char::isWhitespace) }
    val sourceRanges = buildList {
        var cursor = 0
        sources.forEach { source ->
            val compactText = source.segment.text.filterNot(Char::isWhitespace)
            add(SourceRange(source, compactText, cursor, cursor + compactText.length))
            cursor += compactText.length
        }
    }
    if (sourceRanges.last().end != compactLine.length ||
        sourceRanges.joinToString(separator = "") { it.compactText } != compactLine
    ) {
        return words
    }

    val usedIds = mutableSetOf<String>()
    var tokenStart = 0
    return tokens.mapIndexed { tokenIndex, token ->
        val tokenText = token.text.filterNot(Char::isWhitespace)
        val tokenEnd = tokenStart + tokenText.length
        val contributions = sourceRanges.mapNotNull { source ->
            val overlapStart = maxOf(tokenStart, source.start)
            val overlapEnd = minOf(tokenEnd, source.end)
            if (overlapStart >= overlapEnd) return@mapNotNull null
            val localStart = overlapStart - source.start
            val localEnd = overlapEnd - source.start
            val leading = if (localStart == 0) {
                source.source.segment.text.takeWhile(Char::isWhitespace)
            } else ""
            val trailing = if (localEnd == source.compactText.length) {
                source.source.segment.text.takeLastWhile(Char::isWhitespace)
            } else ""
            source.source to source.source.segment.copy(
                text = leading + source.compactText.substring(localStart, localEnd) + trailing,
            )
        }
        tokenStart = tokenEnd
        if (contributions.isEmpty()) return words

        val firstId = contributions.first().first.wordId
        val id = if (usedIds.add(firstId)) firstId else "$firstId:semantic:$tokenIndex"
        val segments = contributions.map(Pair<SourceSegment, LyricsWordSegment>::second)
        val start = segments.firstNotNullOfOrNull(LyricsWordSegment::startTimeMs)
        val end = segments.asReversed().firstNotNullOfOrNull(LyricsWordSegment::endTimeMs)
        LyricsWord(
            id = id,
            text = token.text,
            startTimeMs = start,
            endTimeMs = end,
            timingQuality = aggregateTimingQuality(segments),
            segments = segments,
        )
    }
}

internal data class SemanticTextToken(
    val text: String,
    val startIndex: Int,
    val endIndex: Int,
)

internal fun semanticTextTokens(text: String, languageTag: String?): List<SemanticTextToken> {
    if (text.isBlank()) return emptyList()
    val locale = languageTag?.takeIf(String::isNotBlank)?.let(Locale::forLanguageTag)
        ?.takeUnless { it.language.isBlank() }
        ?: Locale.ROOT
    val useLinguisticSegmentation = text.none(Char::isWhitespace) || locale.language in
        setOf("ja", "zh", "th", "lo", "km", "my")
    return nonWhitespaceRuns(text).flatMap { run ->
        if (useLinguisticSegmentation) {
            linguisticTokens(run.text, run.startIndex, locale).asSequence()
        } else {
            sequenceOf(run)
        }
    }.toList()
}

private fun nonWhitespaceRuns(text: String): Sequence<SemanticTextToken> = sequence {
    var start = -1
    text.forEachIndexed { index, character ->
        if (!character.isWhitespace() && start < 0) {
            start = index
        } else if (character.isWhitespace() && start >= 0) {
            yield(SemanticTextToken(text.substring(start, index), start, index))
            start = -1
        }
    }
    if (start >= 0) yield(SemanticTextToken(text.substring(start), start, text.length))
}

private fun linguisticTokens(
    text: String,
    sourceOffset: Int,
    locale: Locale,
): List<SemanticTextToken> {
    val iterator = BreakIterator.getWordInstance(locale).apply { setText(text) }
    val tokens = mutableListOf<SemanticTextToken>()
    var pendingPrefixStart: Int? = null
    var joinNext = false
    var start = iterator.first()
    var end = iterator.next()
    while (end != BreakIterator.DONE) {
        val value = text.substring(start, end)
        if (value.any(Char::isLetterOrDigit)) {
            if (joinNext && tokens.isNotEmpty()) {
                val previous = tokens.removeAt(tokens.lastIndex)
                tokens += SemanticTextToken(
                    text = text.substring(previous.startIndex - sourceOffset, end),
                    startIndex = previous.startIndex,
                    endIndex = sourceOffset + end,
                )
                joinNext = false
            } else {
                val tokenStart = pendingPrefixStart ?: start
                tokens += SemanticTextToken(
                    text.substring(tokenStart, end),
                    sourceOffset + tokenStart,
                    sourceOffset + end,
                )
            }
            pendingPrefixStart = null
        } else if (value.isNotBlank()) {
            if (tokens.isNotEmpty()) {
                val previous = tokens.removeAt(tokens.lastIndex)
                tokens += previous.copy(
                    text = text.substring(previous.startIndex - sourceOffset, end),
                    endIndex = sourceOffset + end,
                )
                joinNext = value.all { it in WORD_CONNECTORS }
            } else if (pendingPrefixStart == null) {
                pendingPrefixStart = start
            }
        }
        start = end
        end = iterator.next()
    }
    if (tokens.isEmpty()) {
        return listOf(SemanticTextToken(text, sourceOffset, sourceOffset + text.length))
    }
    pendingPrefixStart?.let { prefixStart ->
        val previous = tokens.removeAt(tokens.lastIndex)
        tokens += previous.copy(
            text = text.substring(
                minOf(previous.startIndex - sourceOffset, prefixStart),
                text.length,
            ),
            endIndex = sourceOffset + text.length,
        )
    }
    return tokens
}

private data class SourceSegment(
    val wordId: String,
    val segment: LyricsWordSegment,
)

private data class SourceRange(
    val source: SourceSegment,
    val compactText: String,
    val start: Int,
    val end: Int,
)

private fun aggregateTimingQuality(segments: List<LyricsWordSegment>): TimingQuality = when {
    segments.isNotEmpty() && segments.all { it.timingQuality == TimingQuality.EXPLICIT } ->
        TimingQuality.EXPLICIT
    segments.any { it.startTimeMs != null || it.endTimeMs != null } -> TimingQuality.INFERRED
    else -> TimingQuality.UNKNOWN
}

private val WORD_CONNECTORS = setOf('\'', '\u2019', '\u02BC', '-', '\u2010', '\u2011')
