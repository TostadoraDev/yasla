package xyz.tosdev.yetasimplelyicsapp.domain.lyrics.parser

import xyz.tosdev.yetasimplelyicsapp.domain.lyrics.LyricsAlignment
import xyz.tosdev.yetasimplelyicsapp.domain.lyrics.LyricsDocument
import xyz.tosdev.yetasimplelyicsapp.domain.lyrics.LyricsLine
import xyz.tosdev.yetasimplelyicsapp.domain.lyrics.LyricsTextDirection
import xyz.tosdev.yetasimplelyicsapp.domain.lyrics.SyncType
import java.util.Locale

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
        if (document.lines.any(LyricsLine::isBackground)) {
            diagnostics.warnOnce(
                "LRC_BACKGROUND_OMITTED",
                "LRC cannot preserve AMLL background-line relationships."
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

        val foregroundLines = document.lines.filterNot(LyricsLine::isBackground)
        val backgroundLines = document.lines.filter(LyricsLine::isBackground)
        val assignedBackgroundIds = mutableSetOf<String>()
        val usedXmlIds = mutableSetOf<String>()
        val agentMap = stableXmlIdMap(
            values = document.lines.mapNotNull(LyricsLine::agentId),
            prefix = "v",
            used = usedXmlIds,
        )
        val regionMap = stableXmlIdMap(
            values = document.lines.mapNotNull(LyricsLine::region),
            prefix = "r",
            used = usedXmlIds,
        )
        val backgroundOwners = backgroundLines.associate { background ->
            background.id to selectBackgroundOwner(background, foregroundLines)?.id
        }
        val usedKeys = mutableSetOf<String>()

        val content = buildString {
            append("<tt")
            append(" xmlns=\"http://www.w3.org/ns/ttml\"")
            append(" xmlns:itunes=\"http://music.apple.com/lyric-ttml-internal\"")
            append(" xmlns:ttm=\"http://www.w3.org/ns/ttml#metadata\"")
            append(" xmlns:tts=\"http://www.w3.org/ns/ttml#styling\"")
            append(" itunes:timing=\"")
            append(if (document.syncType == SyncType.WORD || document.syncType == SyncType.MIXED) "Word" else "Line")
            append("\"")
            document.languageTag?.takeIf(String::isNotBlank)?.let {
                append(" xml:lang=\"${escapeXmlAttribute(it)}\"")
            }
            append(">")

            append("<head><metadata>")
            agentMap.forEach { (agent, xmlId) ->
                append("<ttm:agent type=\"person\" xml:id=\"${escapeXmlAttribute(xmlId)}\"")
                if (agent != xmlId) {
                    append(" itunes:identifier=\"${escapeXmlAttribute(agent)}\"")
                }
                append("/>")
            }
            append("</metadata>")
            if (regionMap.isNotEmpty()) {
                append("<layout>")
                regionMap.forEach { (region, xmlId) ->
                    append("<region xml:id=\"${escapeXmlAttribute(xmlId)}\"")
                    if (region != xmlId) {
                        append(" itunes:identifier=\"${escapeXmlAttribute(region)}\"")
                    }
                    append("/>")
                }
                append("</layout>")
            }
            append("</head><body><div>")

            foregroundLines.forEachIndexed { index, line ->
                val backgrounds = backgroundLines.filter { background ->
                    backgroundOwners[background.id] == line.id &&
                            background.id !in assignedBackgroundIds
                }
                assignedBackgroundIds += backgrounds.map(LyricsLine::id)
                appendAmllParagraph(
                    line = line,
                    key = uniqueParagraphKey(line.groupId ?: "L${index + 1}", usedKeys),
                    agentMap = agentMap,
                    regionMap = regionMap,
                    backgrounds = backgrounds,
                    diagnostics = diagnostics,
                )
            }

            backgroundLines.filterNot { it.id in assignedBackgroundIds }
                .forEachIndexed { index, line ->
                    diagnostics += ExportDiagnostic(
                        severity = ParseDiagnosticSeverity.WARNING,
                        code = "TTML_BACKGROUND_ORPHAN",
                        message = "Background line has no foreground line with the same group; it was exported as its own AMLL paragraph.",
                        lineId = line.id,
                    )
                    appendAmllParagraph(
                        line = line,
                        key = uniqueParagraphKey(line.groupId ?: "BG${index + 1}", usedKeys),
                        agentMap = agentMap,
                        regionMap = regionMap,
                        backgrounds = emptyList(),
                        diagnostics = diagnostics,
                        paragraphRole = listOf("x-bg", line.role)
                            .filterNotNull()
                            .distinct()
                            .joinToString(" "),
                    )
                }

            append("</div></body></tt>")
        }
        return LyricsExportResult(content, diagnostics)
    }

    private fun StringBuilder.appendAmllParagraph(
        line: LyricsLine,
        key: String,
        agentMap: Map<String, String>,
        regionMap: Map<String, String>,
        backgrounds: List<LyricsLine>,
        diagnostics: MutableList<ExportDiagnostic>,
        paragraphRole: String? = null,
    ) {
        append("<p")
        line.startTimeMs?.let { append(" begin=\"${formatAmllTime(it)}\"") }
        line.endTimeMs?.let { append(" end=\"${formatAmllTime(it)}\"") }
        append(" itunes:key=\"${escapeXmlAttribute(key)}\"")
        line.groupId?.takeIf(String::isNotBlank)?.let {
            append(" itunes:group=\"${escapeXmlAttribute(it)}\"")
        }
        line.agentId?.let(agentMap::get)?.let {
            append(" ttm:agent=\"${escapeXmlAttribute(it)}\"")
        }
        (paragraphRole ?: line.role)?.takeIf(String::isNotBlank)?.let {
            append(" ttm:role=\"${escapeXmlAttribute(it)}\"")
        }
        appendPresentationAttributes(line, regionMap)
        append(">")

        appendAmllTimedContent(line, diagnostics)
        appendAuxiliarySpans(line)
        backgrounds.forEach { appendBackgroundSpan(it, agentMap, regionMap, diagnostics) }
        append("</p>")
    }

    private fun StringBuilder.appendBackgroundSpan(
        line: LyricsLine,
        agentMap: Map<String, String>,
        regionMap: Map<String, String>,
        diagnostics: MutableList<ExportDiagnostic>,
    ) {
        append("<span ttm:role=\"")
        append(
            escapeXmlAttribute(
                listOf("x-bg", line.role).filterNotNull().distinct().joinToString(" ")
            )
        )
        append("\"")
        line.startTimeMs?.let { append(" begin=\"${formatAmllTime(it)}\"") }
        line.endTimeMs?.let { append(" end=\"${formatAmllTime(it)}\"") }
        line.agentId?.let(agentMap::get)?.let {
            append(" ttm:agent=\"${escapeXmlAttribute(it)}\"")
        }
        appendPresentationAttributes(line, regionMap)
        append(">")
        appendAmllTimedContent(line, diagnostics)
        appendAuxiliarySpans(line)
        append("</span>")
    }

    private fun StringBuilder.appendPresentationAttributes(
        line: LyricsLine,
        regionMap: Map<String, String>,
    ) {
        when (line.alignment) {
            LyricsAlignment.LEFT -> append(" tts:textAlign=\"left\"")
            LyricsAlignment.RIGHT -> append(" tts:textAlign=\"right\"")
            LyricsAlignment.CENTER -> append(" tts:textAlign=\"center\"")
            LyricsAlignment.AUTO -> Unit
        }
        when (line.direction) {
            LyricsTextDirection.LTR -> append(" tts:direction=\"ltr\"")
            LyricsTextDirection.RTL -> append(" tts:direction=\"rtl\"")
            LyricsTextDirection.AUTO -> Unit
        }
        line.region?.takeIf(String::isNotBlank)?.let {
            append(" region=\"${escapeXmlAttribute(regionMap[it] ?: it)}\"")
        }
    }

    private fun selectBackgroundOwner(
        background: LyricsLine,
        foregroundLines: List<LyricsLine>,
    ): LyricsLine? {
        val group = background.groupId ?: return null
        return foregroundLines.asSequence()
            .filter { it.groupId == group }
            .minWithOrNull(
                compareBy<LyricsLine> { temporalDistance(background, it) }
                    .thenBy { kotlin.math.abs(background.order - it.order) },
            )
    }

    private fun temporalDistance(first: LyricsLine, second: LyricsLine): Long {
        val firstStart = first.startTimeMs ?: return Long.MAX_VALUE
        val secondStart = second.startTimeMs ?: return Long.MAX_VALUE
        val firstEnd = first.endTimeMs ?: firstStart
        val secondEnd = second.endTimeMs ?: secondStart
        return when {
            firstStart <= secondEnd && secondStart <= firstEnd -> 0L
            firstEnd < secondStart -> secondStart - firstEnd
            else -> firstStart - secondEnd
        }
    }

    private fun stableXmlIdMap(
        values: List<String>,
        prefix: String,
        used: MutableSet<String>,
    ): Map<String, String> {
        var generatedIndex = 1
        return buildMap {
            values.filter(String::isNotBlank).distinct().forEach { value ->
                val preferred = value.takeIf { it.matches(XML_ID) && it !in used }
                val xmlId = preferred ?: run {
                    var candidate: String
                    do {
                        candidate = "$prefix${generatedIndex++}"
                    } while (candidate in used)
                    candidate
                }
                used += xmlId
                put(value, xmlId)
            }
        }
    }

    private fun StringBuilder.appendAuxiliarySpans(line: LyricsLine) {
        line.text.translations.forEach { (language, text) ->
            append("<span ttm:role=\"x-translation\"")
            language.takeIf(String::isNotBlank)?.let {
                append(" xml:lang=\"${escapeXmlAttribute(it)}\"")
            }
            append(">")
            append(escapeXmlTextWithBreaks(text))
            append("</span>")
        }
        line.text.romanizations.forEach { (language, text) ->
            append("<span ttm:role=\"x-roman\"")
            language.takeIf(String::isNotBlank)?.let {
                append(" xml:lang=\"${escapeXmlAttribute(it)}\"")
            }
            append(">")
            append(escapeXmlTextWithBreaks(text))
            append("</span>")
        }
    }

    private fun StringBuilder.appendAmllTimedContent(
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
            val segments = word.segments.takeIf { candidates ->
                candidates.isNotEmpty() &&
                    candidates.all { it.startTimeMs != null } &&
                    candidates.joinToString("") { it.text } == word.text
            }
            if (segments == null) {
                append("<span begin=\"${formatAmllTime(requireNotNull(word.startTimeMs))}\"")
                word.endTimeMs?.let { append(" end=\"${formatAmllTime(it)}\"") }
                append(">")
                append(escapeXmlTextWithBreaks(word.text))
                append("</span>")
            } else {
                segments.forEachIndexed { index, segment ->
                    append("<span begin=\"${formatAmllTime(requireNotNull(segment.startTimeMs))}\"")
                    segment.endTimeMs?.let { append(" end=\"${formatAmllTime(it)}\"") }
                    append(" itunes:partOfWord=\"")
                    append(if (index < segments.lastIndex) "true" else "false")
                    append("\">")
                    append(escapeXmlTextWithBreaks(segment.text))
                    append("</span>")
                }
            }
        }
    }

    private fun uniqueParagraphKey(base: String, used: MutableSet<String>): String {
        val normalized = base.ifBlank { "L${used.size + 1}" }
        if (used.add(normalized)) return normalized
        var suffix = 2
        while (!used.add("$normalized-$suffix")) suffix += 1
        return "$normalized-$suffix"
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
        val hours = safe / 3_600_000L
        val minutes = (safe % 3_600_000L) / 60_000L
        val seconds = (safe % 60_000L) / 1_000L
        val millis = safe % 1_000L
        return if (hours > 0L) {
            String.format(Locale.ROOT, "%02d:%02d:%02d.%03d", hours, minutes, seconds, millis)
        } else {
            String.format(Locale.ROOT, "%02d:%02d.%03d", minutes, seconds, millis)
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

    private companion object {
        val XML_ID = Regex("""[A-Za-z_][A-Za-z0-9._-]*""")
    }
}
