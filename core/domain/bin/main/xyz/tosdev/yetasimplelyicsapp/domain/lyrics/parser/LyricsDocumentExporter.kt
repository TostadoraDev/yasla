package xyz.tosdev.yetasimplelyicsapp.domain.lyrics.parser

import xyz.tosdev.yetasimplelyicsapp.domain.lyrics.LyricsDocument
import xyz.tosdev.yetasimplelyicsapp.domain.lyrics.LyricsLine

enum class LyricsExportFormat {
    LRC,
    ENHANCED_LRC,
    TTML,
}

data class ExportDiagnostic(
    val severity: ParseDiagnosticSeverity,
    val code: String,
    val message: String,
    val lineId: String? = null,
)

data class LyricsExportResult(
    val content: String,
    val diagnostics: List<ExportDiagnostic> = emptyList(),
) {
    val isLossless: Boolean
        get() = diagnostics.none { it.severity == ParseDiagnosticSeverity.WARNING || it.severity == ParseDiagnosticSeverity.ERROR }
}

class LyricsDocumentExporter {
    fun export(document: LyricsDocument, format: LyricsExportFormat): LyricsExportResult =
        when (format) {
            LyricsExportFormat.LRC -> exportLrc(document, enhanced = false)
            LyricsExportFormat.ENHANCED_LRC -> exportLrc(document, enhanced = true)
            LyricsExportFormat.TTML -> exportTtml(document)
        }

    private fun exportLrc(document: LyricsDocument, enhanced: Boolean): LyricsExportResult {
        val diagnostics = mutableListOf<ExportDiagnostic>()
        if (document.lines.isEmpty()) {
            diagnostics.warnOnce("EXPORT_EMPTY_DOCUMENT", "The document contains no lyric lines.")
        }
        if (document.lines.any { it.text.translations.isNotEmpty() }) {
            diagnostics.warnOnce(
                "LRC_TRANSLATIONS_OMITTED",
                "LRC cannot preserve translation tracks."
            )
        }
        if (document.lines.any { it.text.romanizations.isNotEmpty() }) {
            diagnostics.warnOnce(
                "LRC_ROMANIZATIONS_OMITTED",
                "LRC cannot preserve romanization tracks."
            )
        }
        if (document.lines.any { it.agentId != null }) {
            diagnostics.warnOnce("LRC_AGENTS_OMITTED", "LRC cannot preserve TTML agent identities.")
        }
        if (document.lines.any { it.groupId != null }) {
            diagnostics.warnOnce(
                "LRC_GROUPS_OMITTED",
                "LRC cannot preserve lyric group identities."
            )
        }

        val timedLines = document.lines.filter { it.startTimeMs != null }
        val expectedEnds = timedLines.mapIndexed { index, line ->
            line to timedLines.getOrNull(index + 1)?.startTimeMs
        }
        if (expectedEnds.any { (line, expected) -> line.endTimeMs != null && line.endTimeMs != expected }) {
            diagnostics.warnOnce(
                "LRC_LINE_ENDS_INFERRED",
                "LRC stores line starts only; explicit line ends will be inferred when imported.",
            )
        }
        if (enhanced && document.lines.any { line ->
                line.words.zipWithNext().any { (word, next) ->
                    word.endTimeMs != null && word.endTimeMs != next.startTimeMs
                } || line.words.lastOrNull()
                    ?.let { it.endTimeMs != null && it.endTimeMs != line.endTimeMs } == true
            }
        ) {
            diagnostics.warnOnce(
                "LRC_WORD_ENDS_INFERRED",
                "Enhanced LRC stores word starts only; explicit word ends will be inferred when imported.",
            )
        }

        val output = document.lines.joinToString("\n") { line ->
            val timestamp = line.startTimeMs?.let(::formatLrcTime)
            if (timestamp == null) {
                diagnostics += ExportDiagnostic(
                    severity = ParseDiagnosticSeverity.WARNING,
                    code = "LRC_LINE_TIME_MISSING",
                    message = "The line was exported as plain text because it has no start time.",
                    lineId = line.id,
                )
                line.text.original
            } else {
                val content =
                    if (enhanced) enhancedLrcContent(line, diagnostics) else line.text.original
                "[$timestamp]$content"
            }
        }
        return LyricsExportResult(output, diagnostics)
    }

    private fun enhancedLrcContent(
        line: LyricsLine,
        diagnostics: MutableList<ExportDiagnostic>,
    ): String {
        if (line.words.isEmpty()) return line.text.original
        if (line.words.any { it.startTimeMs == null }) {
            diagnostics += ExportDiagnostic(
                severity = ParseDiagnosticSeverity.WARNING,
                code = "LRC_WORD_TIME_MISSING",
                message = "The line was exported without word markers because one or more words have no start time.",
                lineId = line.id,
            )
            return line.text.original
        }

        val joinedWords = line.words.joinToString(separator = "") { it.text }
        if (normalizeForComparison(joinedWords) != normalizeForComparison(line.text.original)) {
            diagnostics += ExportDiagnostic(
                severity = ParseDiagnosticSeverity.WARNING,
                code = "LRC_WORD_TEXT_MISMATCH",
                message = "Word spans do not reconstruct the displayed line; line text was preferred to avoid corruption.",
                lineId = line.id,
            )
            return line.text.original
        }

        return line.words.joinToString(separator = "") { word ->
            "<${formatLrcTime(requireNotNull(word.startTimeMs))}>${word.text}"
        }
    }

    private fun exportTtml(document: LyricsDocument): LyricsExportResult {
        val diagnostics = mutableListOf<ExportDiagnostic>()
        if (document.lines.isEmpty()) {
            diagnostics.warnOnce("EXPORT_EMPTY_DOCUMENT", "The document contains no lyric lines.")
        }

        val agents = document.lines.mapNotNull { it.agentId }.distinct().ifEmpty { listOf("v1") }
        val defaultAgent = agents.first()

        val timedLines = document.lines.filter { it.startTimeMs != null || it.endTimeMs != null }
        val divBegin = timedLines.mapNotNull { it.startTimeMs }.minOrNull()
        val divEnd = timedLines.mapNotNull { it.endTimeMs }.maxOrNull()
        val bodyDur = divEnd ?: divBegin

        val content = buildString {
            append("<tt")
            append(" xmlns=\"http://www.w3.org/ns/ttml\"")
            append(" xmlns:itunes=\"http://music.apple.com/lyric-ttml-internal\"")
            append(" xmlns:ttm=\"http://www.w3.org/ns/ttml#metadata\"")
            append(" xmlns:tts=\"http://www.w3.org/ns/ttml#styling\"")
            append(" xmlns:amll=\"http://www.example.com/ns/amll\"")
            append(">")

            append("<head><metadata>")
            agents.forEach { agent ->
                append("<ttm:agent type=\"person\" xml:id=\"${escapeXmlAttribute(agent)}\"/>")
            }
            append("</metadata></head>")

            append("<body")
            bodyDur?.let { append(" dur=\"${formatAmllTime(it)}\"") }
            append(">")

            append("<div")
            divBegin?.let { append(" begin=\"${formatAmllTime(it)}\"") }
            divEnd?.let { append(" end=\"${formatAmllTime(it)}\"") }
            append(">")

            document.lines.forEachIndexed { index, line ->
                appendAmllLine(line, index + 1, defaultAgent, null, null, null, diagnostics)
                line.text.translations.forEach { (language, text) ->
                    appendAmllLine(line, index + 1, defaultAgent, "translation", language, text, diagnostics)
                }
                line.text.romanizations.forEach { (language, text) ->
                    appendAmllLine(line, index + 1, defaultAgent, "romanization", language, text, diagnostics)
                }
            }

            append("</div></body></tt>")
        }
        return LyricsExportResult(content, diagnostics)
    }

    private fun StringBuilder.appendAmllLine(
        line: LyricsLine,
        lineNumber: Int,
        defaultAgent: String,
        role: String?,
        languageTag: String?,
        translatedText: String?,
        diagnostics: MutableList<ExportDiagnostic>,
    ) {
        append("<p")
        line.startTimeMs?.let { append(" begin=\"${formatAmllTime(it)}\"") }
        line.endTimeMs?.let { append(" end=\"${formatAmllTime(it)}\"") }
        append(" itunes:key=\"L$lineNumber\"")
        val agent = line.agentId ?: defaultAgent
        append(" ttm:agent=\"${escapeXmlAttribute(agent)}\"")
        role?.let { append(" ttm:role=\"${escapeXmlAttribute(it)}\"") }
        languageTag?.let { append(" xml:lang=\"${escapeXmlAttribute(it)}\"") }
        append(">")

        if (translatedText != null) {
            append(escapeXmlTextWithBreaks(translatedText))
        } else {
            appendAmllContent(line, diagnostics)
        }
        append("</p>")
    }

    private fun StringBuilder.appendAmllContent(
        line: LyricsLine,
        diagnostics: MutableList<ExportDiagnostic>,
    ) {
        if (line.words.isEmpty()) {
            append(escapeXmlTextWithBreaks(line.text.original))
            return
        }
        if (line.words.any { it.startTimeMs == null }) {
            diagnostics += ExportDiagnostic(
                ParseDiagnosticSeverity.WARNING,
                "TTML_WORD_TIME_MISSING",
                "Word spans were omitted because one or more words have no start time.",
                line.id,
            )
            append(escapeXmlTextWithBreaks(line.text.original))
            return
        }

        val joinedWords = line.words.joinToString(separator = "") { it.text }
        if (normalizeForComparison(joinedWords) != normalizeForComparison(line.text.original)) {
            diagnostics += ExportDiagnostic(
                ParseDiagnosticSeverity.WARNING,
                "TTML_WORD_TEXT_MISMATCH",
                "Word spans do not reconstruct the displayed line; line text was preferred to avoid corruption.",
                line.id,
            )
            append(escapeXmlTextWithBreaks(line.text.original))
            return
        }

        line.words.forEach { word ->
            append("<span begin=\"${formatAmllTime(requireNotNull(word.startTimeMs))}\"")
            word.endTimeMs?.let { append(" end=\"${formatAmllTime(it)}\"") }
            append(">")
            append(escapeXmlTextWithBreaks(word.text))
            append("</span>")
        }
    }

    private fun MutableList<ExportDiagnostic>.warnOnce(code: String, message: String) {
        if (none { it.code == code }) {
            add(ExportDiagnostic(ParseDiagnosticSeverity.WARNING, code, message))
        }
    }

    private fun normalizeForComparison(value: String): String =
        value.replace(Regex("""\s+"""), " ").trim()

    private fun formatLrcTime(milliseconds: Long): String {
        val safe = milliseconds.coerceAtLeast(0L)
        val minutes = safe / 60_000L
        val seconds = (safe % 60_000L) / 1_000L
        val millis = safe % 1_000L
        return "%02d:%02d.%03d".format(minutes, seconds, millis)
    }

    private fun formatAmllTime(milliseconds: Long): String {
        val safe = milliseconds.coerceAtLeast(0L)
        val minutes = safe / 60_000L
        val seconds = (safe % 60_000L) / 1_000L
        val millis = safe % 1_000L
        return if (minutes > 0) {
            "%d:%02d.%03d".format(minutes, seconds, millis)
        } else {
            "%d.%03d".format(seconds, millis)
        }
    }

    private fun escapeXmlAttribute(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")

    private fun escapeXmlTextWithBreaks(value: String): String =
        value.replace("\r\n", "\n")
            .replace('\r', '\n')
            .split('\n')
            .joinToString("<br/>") { segment ->
                segment.replace("&", "&amp;")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;")
            }
}
