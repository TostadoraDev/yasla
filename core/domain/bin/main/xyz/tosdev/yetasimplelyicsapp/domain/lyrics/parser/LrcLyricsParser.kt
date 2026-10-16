package xyz.tosdev.yetasimplelyicsapp.domain.lyrics.parser

import xyz.tosdev.yetasimplelyicsapp.domain.lyrics.LocalizedText
import xyz.tosdev.yetasimplelyicsapp.domain.lyrics.LyricsCapability
import xyz.tosdev.yetasimplelyicsapp.domain.lyrics.LyricsDocument
import xyz.tosdev.yetasimplelyicsapp.domain.lyrics.LyricsLine
import xyz.tosdev.yetasimplelyicsapp.domain.lyrics.LyricsWord
import xyz.tosdev.yetasimplelyicsapp.domain.lyrics.SyncType
import xyz.tosdev.yetasimplelyicsapp.domain.lyrics.TimingQuality

class LrcLyricsParser : LyricsTextParser {
    override fun parse(input: String, context: LyricsParseContext): LyricsParseResult {
        val diagnostics = mutableListOf<ParseDiagnostic>()
        if (input.isBlank()) {
            return LyricsParseResult(
                document = null,
                diagnostics = listOf(
                    ParseDiagnostic(
                        severity = ParseDiagnosticSeverity.ERROR,
                        code = "LRC_EMPTY",
                        message = "The LRC document is empty.",
                    ),
                ),
            )
        }

        val physicalLines = input.replace("\r\n", "\n").replace('\r', '\n').split('\n')
        var offsetMs = 0L
        physicalLines.forEachIndexed { index, line ->
            OFFSET_TAG.find(line)?.let { match ->
                offsetMs = match.groupValues[1].toLongOrNull() ?: run {
                    diagnostics += warning("LRC_INVALID_OFFSET", "Invalid offset tag.", index + 1)
                    offsetMs
                }
            }
        }

        val candidates = mutableListOf<LineCandidate>()
        physicalLines.forEachIndexed { lineIndex, rawLine ->
            val trimmed = rawLine.trim()
            if (trimmed.isEmpty() || isMetadataOnly(trimmed)) return@forEachIndexed

            val timestampMatches = LINE_TIMESTAMP.findAll(rawLine).toList()
            if (timestampMatches.isEmpty()) {
                if (rawLine.trimStart().startsWith("[")) {
                    diagnostics += warning(
                        "LRC_INVALID_TIMESTAMP",
                        "A bracketed tag was not recognized as metadata or a timestamp.",
                        lineIndex + 1,
                    )
                } else {
                    candidates += LineCandidate(
                        sourceOrder = candidates.size,
                        adjustedStartMs = null,
                        text = rawLine.trim(),
                        rawWords = emptyList(),
                    )
                }
                return@forEachIndexed
            }

            val contentWithoutLineTimes = removeMatches(rawLine, timestampMatches).trim()
            val enhanced = parseEnhanced(contentWithoutLineTimes, lineIndex + 1, diagnostics)
            val firstRawStart = timestampMatches.firstNotNullOfOrNull {
                parseLrcTimestamp(it.groupValues[1])
            }
            timestampMatches.forEach { match ->
                val rawStart = parseLrcTimestamp(match.groupValues[1])
                if (rawStart == null) {
                    diagnostics += warning(
                        "LRC_INVALID_TIMESTAMP",
                        "Invalid line timestamp '${match.groupValues[1]}'.",
                        lineIndex + 1,
                    )
                    return@forEach
                }

                val adjustedStart = applyOffset(rawStart, offsetMs, lineIndex + 1, diagnostics)
                val shiftedWords = enhanced.words.map { rawWord ->
                    val relativeStart = rawWord.startMs - (firstRawStart ?: rawStart)
                    RawWord(
                        text = rawWord.text,
                        startMs = (adjustedStart + relativeStart).coerceAtLeast(0L),
                    )
                }
                candidates += LineCandidate(
                    sourceOrder = candidates.size,
                    adjustedStartMs = adjustedStart,
                    text = enhanced.text,
                    rawWords = shiftedWords,
                )
            }
        }

        if (candidates.isEmpty()) {
            return LyricsParseResult(
                document = null,
                diagnostics = diagnostics + ParseDiagnostic(
                    severity = ParseDiagnosticSeverity.ERROR,
                    code = "LRC_NO_LYRICS",
                    message = "No lyric lines could be parsed.",
                ),
            )
        }

        val ordered = candidates.sortedWith(
            compareBy<LineCandidate> { it.adjustedStartMs == null }
                .thenBy { it.adjustedStartMs ?: Long.MAX_VALUE }
                .thenBy { it.sourceOrder },
        )
        val lines = ordered.mapIndexed { index, candidate ->
            val nextStart =
                ordered.drop(index + 1).firstNotNullOfOrNull(LineCandidate::adjustedStartMs)
            val lineEnd = nextStart?.takeIf {
                candidate.adjustedStartMs == null || it >= candidate.adjustedStartMs
            }
            val words = candidate.rawWords.mapIndexed { wordIndex, word ->
                val nextWordStart = candidate.rawWords.getOrNull(wordIndex + 1)?.startMs
                val end = nextWordStart ?: lineEnd
                LyricsWord(
                    id = "${context.documentId}-line-$index-word-$wordIndex",
                    text = word.text,
                    startTimeMs = word.startMs,
                    endTimeMs = end?.coerceAtLeast(word.startMs),
                    timingQuality = if (nextWordStart != null) TimingQuality.EXPLICIT else {
                        end?.let { TimingQuality.INFERRED } ?: TimingQuality.UNKNOWN
                    },
                )
            }
            LyricsLine(
                id = "${context.documentId}-line-$index",
                order = index,
                text = LocalizedText(candidate.text),
                startTimeMs = candidate.adjustedStartMs,
                endTimeMs = lineEnd,
                words = words,
                timingQuality = when {
                    candidate.adjustedStartMs == null -> TimingQuality.UNKNOWN
                    lineEnd != null -> TimingQuality.INFERRED
                    else -> TimingQuality.EXPLICIT
                },
            )
        }

        val hasTimed = lines.any { it.startTimeMs != null }
        val hasWords = lines.any { it.words.isNotEmpty() }
        val syncType = when {
            hasWords && lines.any { it.words.isEmpty() && it.startTimeMs != null } -> SyncType.MIXED
            hasWords -> SyncType.WORD
            hasTimed && lines.any { it.startTimeMs == null } -> SyncType.MIXED
            hasTimed -> SyncType.LINE
            else -> SyncType.PLAIN
        }
        val capabilities = buildSet {
            add(LyricsCapability.PLAIN_TEXT)
            if (hasTimed) add(LyricsCapability.LINE_SYNC)
            if (hasWords) add(LyricsCapability.WORD_SYNC)
        }

        return LyricsParseResult(
            document = LyricsDocument(
                id = context.documentId,
                trackKey = context.trackKey,
                source = context.source,
                syncType = syncType,
                lines = lines,
                languageTag = context.languageTag,
                capabilities = capabilities,
            ),
            diagnostics = diagnostics,
        )
    }

    private fun parseEnhanced(
        content: String,
        sourceLine: Int,
        diagnostics: MutableList<ParseDiagnostic>,
    ): EnhancedLine {
        val markers = ENHANCED_TIMESTAMP.findAll(content).toList()
        if (markers.isEmpty()) return EnhancedLine(content.trim(), emptyList())

        val text = StringBuilder()
        val words = mutableListOf<RawWord>()
        val prefix = content.substring(0, markers.first().range.first)
        if (prefix.isNotEmpty()) text.append(prefix)

        markers.forEachIndexed { index, marker ->
            val start = parseLrcTimestamp(marker.groupValues[1])
            val segmentStart = marker.range.last + 1
            val segmentEnd = markers.getOrNull(index + 1)?.range?.first ?: content.length
            val segment = content.substring(segmentStart, segmentEnd)
            text.append(segment)
            if (start == null) {
                diagnostics += warning(
                    "LRC_INVALID_WORD_TIMESTAMP",
                    "Invalid enhanced timestamp '${marker.groupValues[1]}'.",
                    sourceLine,
                )
            } else if (segment.isNotEmpty()) {
                words += RawWord(segment, start)
            }
        }
        return EnhancedLine(text.toString().trim(), words)
    }

    private fun removeMatches(value: String, matches: List<MatchResult>): String {
        val result = StringBuilder(value)
        matches.asReversed().forEach { result.delete(it.range.first, it.range.last + 1) }
        return result.toString()
    }

    private fun applyOffset(
        timestampMs: Long,
        offsetMs: Long,
        line: Int,
        diagnostics: MutableList<ParseDiagnostic>,
    ): Long {
        val adjusted = timestampMs + offsetMs
        if (adjusted >= 0L) return adjusted
        diagnostics += warning(
            "LRC_NEGATIVE_TIME_CLAMPED",
            "The global offset moved a timestamp below zero; it was clamped.",
            line,
        )
        return 0L
    }

    private fun isMetadataOnly(line: String): Boolean =
        METADATA_TAG.matches(line) && LINE_TIMESTAMP.find(line) == null

    private fun warning(code: String, message: String, line: Int) = ParseDiagnostic(
        severity = ParseDiagnosticSeverity.WARNING,
        code = code,
        message = message,
        line = line,
    )

    private data class EnhancedLine(val text: String, val words: List<RawWord>)
    private data class RawWord(val text: String, val startMs: Long)
    private data class LineCandidate(
        val sourceOrder: Int,
        val adjustedStartMs: Long?,
        val text: String,
        val rawWords: List<RawWord>,
    )

    companion object {
        private val LINE_TIMESTAMP =
            Regex("""\[((?:\d+:){1,2}\d{1,2}(?:[.:]\d{1,9})?)]""")
        private val ENHANCED_TIMESTAMP =
            Regex("""<((?:\d+:){1,2}\d{1,2}(?:[.:]\d{1,9})?)>""")
        private val OFFSET_TAG =
            Regex("""\[offset\s*:\s*([+-]?\d+)]""", RegexOption.IGNORE_CASE)
        private val METADATA_TAG = Regex("""\[[A-Za-z][A-Za-z0-9_-]*\s*:.*]""")

        internal fun parseLrcTimestamp(value: String): Long? {
            val parts = value.trim().split(':')
            if (parts.size !in 2..3) return null
            val hours: Long
            val minutes: Long
            val secondsPart: String
            if (parts.size == 3) {
                hours = parts[0].toLongOrNull() ?: return null
                minutes = parts[1].toLongOrNull() ?: return null
                secondsPart = parts[2]
                if (minutes !in 0L..59L) return null
            } else {
                hours = 0L
                minutes = parts[0].toLongOrNull() ?: return null
                secondsPart = parts[1]
            }

            val secondPieces = secondsPart.split('.', limit = 2)
            val seconds = secondPieces[0].toLongOrNull() ?: return null
            if (seconds !in 0L..59L) return null
            val fraction = secondPieces.getOrNull(1).orEmpty()
            if (fraction.any { !it.isDigit() }) return null
            val milliseconds = when {
                fraction.isEmpty() -> 0L
                fraction.length == 1 -> fraction.toLong() * 100L
                fraction.length == 2 -> fraction.toLong() * 10L
                else -> fraction.take(3).toLong()
            }
            return hours * 3_600_000L + minutes * 60_000L + seconds * 1_000L + milliseconds
        }
    }
}
