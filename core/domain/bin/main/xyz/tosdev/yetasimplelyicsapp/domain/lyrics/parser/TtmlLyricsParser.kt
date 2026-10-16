package xyz.tosdev.yetasimplelyicsapp.domain.lyrics.parser

import org.xml.sax.Attributes
import org.xml.sax.InputSource
import org.xml.sax.Locator
import org.xml.sax.SAXException
import org.xml.sax.SAXNotRecognizedException
import org.xml.sax.SAXNotSupportedException
import org.xml.sax.SAXParseException
import org.xml.sax.helpers.DefaultHandler
import xyz.tosdev.yetasimplelyicsapp.domain.lyrics.LocalizedText
import xyz.tosdev.yetasimplelyicsapp.domain.lyrics.LyricsCapability
import xyz.tosdev.yetasimplelyicsapp.domain.lyrics.LyricsDocument
import xyz.tosdev.yetasimplelyicsapp.domain.lyrics.LyricsLine
import xyz.tosdev.yetasimplelyicsapp.domain.lyrics.LyricsWord
import xyz.tosdev.yetasimplelyicsapp.domain.lyrics.SyncType
import xyz.tosdev.yetasimplelyicsapp.domain.lyrics.TimingQuality
import java.io.StringReader
import javax.xml.XMLConstants
import javax.xml.parsers.SAXParserFactory
import kotlin.math.roundToLong

private const val TTML_LINE_BREAK = "\uE000"
private val FORBIDDEN_XML_DECLARATION = Regex(
    """<!\s*(?:DOCTYPE|ENTITY)\b""",
    RegexOption.IGNORE_CASE,
)

class TtmlLyricsParser : LyricsTextParser {
    override fun parse(input: String, context: LyricsParseContext): LyricsParseResult {
        if (input.isBlank()) return failure("TTML_EMPTY", "The TTML document is empty.")

        val diagnostics = mutableListOf<ParseDiagnostic>()
        val root = try {
            parseXml(input)
        } catch (error: SecurityConfigurationException) {
            return failure(
                "TTML_SECURITY_CONFIGURATION_FAILED",
                error.message ?: "Secure XML setup failed."
            )
        } catch (error: SAXParseException) {
            return LyricsParseResult(
                document = null,
                diagnostics = listOf(
                    ParseDiagnostic(
                        severity = ParseDiagnosticSeverity.ERROR,
                        code = "TTML_MALFORMED_XML",
                        message = error.message ?: "Malformed TTML XML.",
                        line = error.lineNumber.takeIf { it > 0 },
                        column = error.columnNumber.takeIf { it > 0 },
                    ),
                ),
            )
        } catch (error: Exception) {
            return failure(
                "TTML_XML_ERROR",
                error.message ?: "The TTML document could not be read."
            )
        }

        if (root.localName != "tt") {
            diagnostics += diagnostic(
                ParseDiagnosticSeverity.WARNING,
                "TTML_UNEXPECTED_ROOT",
                "Expected a TTML <tt> root element but found <${root.localName}>.",
                root,
            )
        }

        val timingParameters = TimingParameters.from(root, diagnostics)
        val resolver = TimingResolver(timingParameters, diagnostics)
        val paragraphs = root.descendants().filter { it.localName == "p" }.toList()
        if (paragraphs.isEmpty()) {
            return LyricsParseResult(
                document = null,
                diagnostics = diagnostics + ParseDiagnostic(
                    severity = ParseDiagnosticSeverity.ERROR,
                    code = "TTML_NO_PARAGRAPHS",
                    message = "The TTML document contains no <p> lyric elements.",
                ),
            )
        }

        val drafts = paragraphs.mapNotNull { paragraph ->
            parseParagraph(paragraph, resolver, diagnostics)
        }
        if (drafts.isEmpty()) {
            return LyricsParseResult(
                document = null,
                diagnostics = diagnostics + ParseDiagnostic(
                    severity = ParseDiagnosticSeverity.ERROR,
                    code = "TTML_NO_LYRICS",
                    message = "No lyric text could be extracted from TTML paragraphs.",
                ),
            )
        }

        val auxiliary =
            drafts.filter { it.role == LineRole.TRANSLATION || it.role == LineRole.ROMANIZATION }
        val primary = drafts.filterNot { it in auxiliary }.ifEmpty { drafts }
            .sortedWith(compareBy<LineDraft> { it.startMs == null }.thenBy {
                it.startMs ?: Long.MAX_VALUE
            }.thenBy { it.sourceOrder })

        val lines = primary.mapIndexed { index, draft ->
            val nextStart = primary.getOrNull(index + 1)?.startMs
            val finalEnd =
                draft.endMs ?: nextStart?.takeIf { draft.startMs == null || it >= draft.startMs }
            val translations = auxiliary
                .filter { it.role == LineRole.TRANSLATION && matchesLine(draft, it) }
                .associate { (it.languageTag ?: "und") to it.text }
            val romanizations = auxiliary
                .filter { it.role == LineRole.ROMANIZATION && matchesLine(draft, it) }
                .associate { (it.languageTag ?: "und-Latn") to it.text }

            val words = draft.words.mapIndexed { wordIndex, word ->
                val nextWordStart = draft.words.getOrNull(wordIndex + 1)?.startMs
                val end = word.endMs ?: nextWordStart ?: finalEnd
                LyricsWord(
                    id = "${context.documentId}-line-$index-word-$wordIndex",
                    text = word.text,
                    startTimeMs = word.startMs,
                    endTimeMs = end?.let { candidate ->
                        word.startMs?.let { candidate.coerceAtLeast(it) } ?: candidate
                    },
                    timingQuality = when {
                        word.explicitStart && word.explicitEnd -> TimingQuality.EXPLICIT
                        word.startMs != null || end != null -> TimingQuality.INFERRED
                        else -> TimingQuality.UNKNOWN
                    },
                )
            }
            LyricsLine(
                id = "${context.documentId}-line-$index",
                order = index,
                text = LocalizedText(
                    original = draft.text,
                    translations = translations,
                    romanizations = romanizations,
                ),
                startTimeMs = draft.startMs,
                endTimeMs = finalEnd,
                words = words,
                agentId = draft.agentId,
                groupId = draft.groupId,
                timingQuality = when {
                    draft.explicitStart && draft.explicitEnd -> TimingQuality.EXPLICIT
                    draft.startMs != null || finalEnd != null -> TimingQuality.INFERRED
                    else -> TimingQuality.UNKNOWN
                },
            )
        }

        val hasTimedLines = lines.any { it.startTimeMs != null }
        val hasWords = lines.any { it.words.isNotEmpty() }
        val capabilities = buildSet {
            add(LyricsCapability.PLAIN_TEXT)
            if (hasTimedLines) add(LyricsCapability.LINE_SYNC)
            if (hasWords) add(LyricsCapability.WORD_SYNC)
            if (lines.any { it.text.translations.isNotEmpty() }) add(LyricsCapability.TRANSLATION)
            if (lines.any { it.text.romanizations.isNotEmpty() }) add(LyricsCapability.ROMANIZATION)
            if (lines.mapNotNull(LyricsLine::agentId)
                    .distinct().size > 1
            ) add(LyricsCapability.MULTI_AGENT)
        }
        val syncType = when {
            hasWords && lines.any { it.words.isEmpty() && it.startTimeMs != null } -> SyncType.MIXED
            hasWords -> SyncType.WORD
            hasTimedLines && lines.any { it.startTimeMs == null } -> SyncType.MIXED
            hasTimedLines -> SyncType.LINE
            else -> SyncType.PLAIN
        }

        return LyricsParseResult(
            document = LyricsDocument(
                id = context.documentId,
                trackKey = context.trackKey,
                source = context.source,
                syncType = syncType,
                lines = lines,
                languageTag = context.languageTag ?: root.attribute("lang"),
                capabilities = capabilities,
            ),
            diagnostics = diagnostics,
        )
    }

    private fun parseParagraph(
        paragraph: XmlNode,
        resolver: TimingResolver,
        diagnostics: MutableList<ParseDiagnostic>,
    ): LineDraft? {
        val text = normalizeText(paragraph.fullText())
        if (text.isBlank()) {
            diagnostics += diagnostic(
                ParseDiagnosticSeverity.INFO,
                "TTML_EMPTY_PARAGRAPH",
                "An empty TTML paragraph was ignored.",
                paragraph,
            )
            return null
        }

        val paragraphTiming = resolver.resolve(paragraph)
        val timedSpans = paragraph.descendants()
            .filter { it.localName == "span" && it.hasTiming() }
            .filter { span -> span.descendants().none { it.localName == "span" && it.hasTiming() } }
            .toList()
        val words = timedSpans.mapNotNull { span ->
            val wordText = normalizeText(span.fullText())
            if (wordText.isBlank()) return@mapNotNull null
            val timing = resolver.resolve(span)
            TimedWordDraft(
                text = wordText,
                startMs = timing.startMs,
                endMs = timing.endMs,
                explicitStart = span.attribute("begin") != null,
                explicitEnd = span.attribute("end") != null || span.attribute("dur") != null,
            )
        }.sortedWith(compareBy<TimedWordDraft> { it.startMs == null }.thenBy {
            it.startMs ?: Long.MAX_VALUE
        })

        val explicitParagraphStart = paragraph.attribute("begin") != null
        val explicitParagraphEnd =
            paragraph.attribute("end") != null || paragraph.attribute("dur") != null
        val lineStart =
            if (explicitParagraphStart) paragraphTiming.startMs else words.firstOrNull()?.startMs
        val lineEnd = if (explicitParagraphEnd) {
            val baseEnd = paragraphTiming.endMs
            val lastWordEnd = words.lastOrNull()?.endMs
            if (baseEnd != null && lastWordEnd != null) maxOf(baseEnd, lastWordEnd) else baseEnd ?: lastWordEnd
        } else words.lastOrNull()?.endMs
        val roleValue = sequenceOf(paragraph.attribute("role"), paragraph.attribute("type"))
            .filterNotNull()
            .joinToString(" ")
            .lowercase()
        val role = when {
            "translat" in roleValue -> LineRole.TRANSLATION
            "roman" in roleValue || "translit" in roleValue -> LineRole.ROMANIZATION
            else -> LineRole.PRIMARY
        }

        return LineDraft(
            sourceOrder = paragraph.documentOrder,
            text = text,
            startMs = lineStart,
            endMs = lineEnd,
            explicitStart = explicitParagraphStart,
            explicitEnd = explicitParagraphEnd,
            words = words,
            role = role,
            languageTag = paragraph.attribute("lang"),
            agentId = paragraph.attribute("agent"),
            groupId = paragraph.attribute("key") ?: paragraph.attribute("group"),
        )
    }

    private fun matchesLine(primary: LineDraft, auxiliary: LineDraft): Boolean {
        if (primary.groupId != null && primary.groupId == auxiliary.groupId) return true
        val primaryStart = primary.startMs ?: return false
        val auxiliaryStart = auxiliary.startMs ?: return false
        return kotlin.math.abs(primaryStart - auxiliaryStart) <= 20L
    }

    private fun parseXml(input: String): XmlNode {
        // Android's SAX implementation varies by API level and does not consistently support
        // JAXP 1.5 ACCESS_EXTERNAL_* properties. Reject declarations before the parser sees
        // them, then apply every available parser hardening control as defense in depth.
        if (FORBIDDEN_XML_DECLARATION.containsMatchIn(input)) {
            throw SAXParseException(
                "DOCTYPE and ENTITY declarations are not allowed in TTML imports.",
                null,
            )
        }

        val factory = SAXParserFactory.newInstance().apply {
            isNamespaceAware = true
            trySetFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
            trySetFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            trySetFeature("http://xml.org/sax/features/external-general-entities", false)
            trySetFeature("http://xml.org/sax/features/external-parameter-entities", false)
            trySetFeature(
                "http://apache.org/xml/features/nonvalidating/load-external-dtd",
                false,
            )
            runCatching { isXIncludeAware = false }
        }

        val parser = factory.newSAXParser()
        parser.trySetProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "")
        parser.trySetProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "")

        val handler = TreeHandler()
        parser.xmlReader.entityResolver = handler
        parser.parse(InputSource(StringReader(input.removePrefix("\uFEFF"))), handler)
        return handler.root
            ?: throw IllegalArgumentException("TTML did not contain a root element.")
    }

    private fun SAXParserFactory.trySetFeature(name: String, enabled: Boolean) {
        try {
            setFeature(name, enabled)
        } catch (_: SAXNotRecognizedException) {
            // The lexical pre-check and rejecting EntityResolver remain the portable baseline.
        } catch (_: SAXNotSupportedException) {
            // The lexical pre-check and rejecting EntityResolver remain the portable baseline.
        } catch (_: AbstractMethodError) {
            // Some old Android XML providers expose the API without implementing it.
        }
    }

    private fun javax.xml.parsers.SAXParser.trySetProperty(name: String, value: String) {
        try {
            setProperty(name, value)
        } catch (_: SAXNotRecognizedException) {
            // Android commonly does not implement JAXP 1.5 external-access properties.
        } catch (_: SAXNotSupportedException) {
            // External identifiers are still rejected by TreeHandler.resolveEntity.
        }
    }

    private fun failure(code: String, message: String) = LyricsParseResult(
        document = null,
        diagnostics = listOf(
            ParseDiagnostic(ParseDiagnosticSeverity.ERROR, code, message),
        ),
    )

    private fun diagnostic(
        severity: ParseDiagnosticSeverity,
        code: String,
        message: String,
        node: XmlNode,
    ) = ParseDiagnostic(severity, code, message, node.line, node.column)

    private fun normalizeText(value: String): String = value
        .split(TTML_LINE_BREAK)
        .joinToString("\n") { segment -> segment.replace(Regex("""\s+"""), " ").trim() }
        .trim()

    private enum class LineRole { PRIMARY, TRANSLATION, ROMANIZATION }

    private data class LineDraft(
        val sourceOrder: Int,
        val text: String,
        val startMs: Long?,
        val endMs: Long?,
        val explicitStart: Boolean,
        val explicitEnd: Boolean,
        val words: List<TimedWordDraft>,
        val role: LineRole,
        val languageTag: String?,
        val agentId: String?,
        val groupId: String?,
    )

    private data class TimedWordDraft(
        val text: String,
        val startMs: Long?,
        val endMs: Long?,
        val explicitStart: Boolean,
        val explicitEnd: Boolean,
    )

    private data class Timing(
        val startMs: Long,
        val endMs: Long?,
    )

    private data class ParsedTime(
        val milliseconds: Long,
        val isClockTime: Boolean,
    )

    private data class TimingParameters(
        val frameRate: Double,
        val tickRate: Double,
        val subFrameRate: Double,
    ) {
        companion object {
            fun from(root: XmlNode, diagnostics: MutableList<ParseDiagnostic>): TimingParameters {
                var frameRate = root.attribute("frameRate")?.toDoubleOrNull() ?: 30.0
                val multiplier = root.attribute("frameRateMultiplier")
                    ?.trim()
                    ?.split(Regex("""\s+"""))
                if (multiplier?.size == 2) {
                    val numerator = multiplier[0].toDoubleOrNull()
                    val denominator = multiplier[1].toDoubleOrNull()
                    if (numerator != null && denominator != null && denominator != 0.0) {
                        frameRate *= numerator / denominator
                    } else {
                        diagnostics += ParseDiagnostic(
                            ParseDiagnosticSeverity.WARNING,
                            "TTML_INVALID_FRAME_RATE_MULTIPLIER",
                            "Invalid ttp:frameRateMultiplier was ignored.",
                            root.line,
                            root.column,
                        )
                    }
                }
                val tickRate = root.attribute("tickRate")?.toDoubleOrNull() ?: frameRate
                val subFrameRate = root.attribute("subFrameRate")?.toDoubleOrNull() ?: 1.0
                return TimingParameters(
                    frameRate = frameRate.takeIf { it > 0.0 } ?: 30.0,
                    tickRate = tickRate.takeIf { it > 0.0 } ?: frameRate,
                    subFrameRate = subFrameRate.takeIf { it > 0.0 } ?: 1.0,
                )
            }
        }
    }

    private class TimingResolver(
        private val parameters: TimingParameters,
        private val diagnostics: MutableList<ParseDiagnostic>,
    ) {
        private val cache = mutableMapOf<XmlNode, Timing>()

        fun resolve(node: XmlNode): Timing = cache.getOrPut(node) {
            val parentTiming = node.parent?.let(::resolve) ?: Timing(0L, null)
            val beginRaw = node.attribute("begin")
            val begin = beginRaw?.let { parseTime(it, node) }
            val start = when {
                begin == null -> parentTiming.startMs
                begin.isClockTime -> begin.milliseconds
                else -> parentTiming.startMs + begin.milliseconds
            }.coerceAtLeast(0L)

            val endRaw = node.attribute("end")
            val durationRaw = node.attribute("dur")
            val end = when {
                endRaw != null -> parseTime(endRaw, node)?.let {
                    if (it.isClockTime) it.milliseconds else parentTiming.startMs + it.milliseconds
                }

                durationRaw != null -> parseTime(durationRaw, node)?.milliseconds?.let(start::plus)
                else -> parentTiming.endMs
            }

            if (end != null && end < start) {
                diagnostics += ParseDiagnostic(
                    ParseDiagnosticSeverity.WARNING,
                    "TTML_END_BEFORE_BEGIN",
                    "An end time before its begin time was ignored.",
                    node.line,
                    node.column,
                )
                Timing(start, null)
            } else {
                Timing(start, end)
            }
        }

        private fun parseTime(value: String, node: XmlNode): ParsedTime? {
            val raw = value.trim()
            CLOCK_TIME.matchEntire(raw)?.let { match ->
                val hours = match.groupValues[1].toLong()
                val minutes = match.groupValues[2].toLong()
                val seconds = match.groupValues[3].toLong()
                if (minutes > 59L || seconds > 59L) return invalidTime(raw, node)
                val fraction = match.groupValues[4]
                val fractionMs = when {
                    fraction.isEmpty() -> 0L
                    fraction.length == 1 -> fraction.toLong() * 100L
                    fraction.length == 2 -> fraction.toLong() * 10L
                    else -> fraction.take(3).toLong()
                }
                return ParsedTime(
                    milliseconds = hours * 3_600_000L + minutes * 60_000L + seconds * 1_000L + fractionMs,
                    isClockTime = true,
                )
            }
            FRAME_CLOCK_TIME.matchEntire(raw)?.let { match ->
                val hours = match.groupValues[1].toLong()
                val minutes = match.groupValues[2].toLong()
                val seconds = match.groupValues[3].toLong()
                val frames = match.groupValues[4].toDouble()
                val subFrames = match.groupValues[5].toDoubleOrNull() ?: 0.0
                if (minutes > 59L || seconds > 59L || frames >= parameters.frameRate) {
                    return invalidTime(raw, node)
                }
                val frameMillis =
                    ((frames + subFrames / parameters.subFrameRate) / parameters.frameRate) * 1_000.0
                return ParsedTime(
                    milliseconds = hours * 3_600_000L + minutes * 60_000L + seconds * 1_000L + frameMillis.roundToLong(),
                    isClockTime = true,
                )
            }
            OFFSET_TIME.matchEntire(raw)?.let { match ->
                val amount = match.groupValues[1].toDoubleOrNull() ?: return invalidTime(raw, node)
                val milliseconds = when (match.groupValues[2]) {
                    "h" -> amount * 3_600_000.0
                    "m" -> amount * 60_000.0
                    "s" -> amount * 1_000.0
                    "ms" -> amount
                    "f" -> amount / parameters.frameRate * 1_000.0
                    "t" -> amount / parameters.tickRate * 1_000.0
                    else -> return invalidTime(raw, node)
                }
                return ParsedTime(milliseconds.roundToLong(), isClockTime = false)
            }
            return invalidTime(raw, node)
        }

        private fun invalidTime(raw: String, node: XmlNode): ParsedTime? {
            diagnostics += ParseDiagnostic(
                ParseDiagnosticSeverity.WARNING,
                "TTML_INVALID_TIME",
                "Unsupported or invalid TTML time expression '$raw'.",
                node.line,
                node.column,
            )
            return null
        }

        companion object {
            private val CLOCK_TIME = Regex("""(\d+):(\d{2}):(\d{2})(?:\.(\d+))?""")
            private val FRAME_CLOCK_TIME = Regex("""(\d+):(\d{2}):(\d{2}):(\d+)(?:\.(\d+))?""")
            private val OFFSET_TIME = Regex("""(\d+(?:\.\d+)?)(h|m|s|ms|f|t)""")
        }
    }

    private class TreeHandler : DefaultHandler() {
        var root: XmlNode? = null
            private set
        private val stack = ArrayDeque<XmlNode>()
        private var locator: Locator? = null
        private var documentOrder = 0

        override fun setDocumentLocator(locator: Locator) {
            this.locator = locator
        }

        override fun resolveEntity(publicId: String?, systemId: String?): InputSource {
            throw SAXException("External XML entities are not allowed in TTML imports.")
        }

        override fun startElement(
            uri: String,
            localName: String,
            qName: String,
            attributes: Attributes
        ) {
            val normalizedName = (localName.ifEmpty { qName.substringAfter(':') }).lowercase()
            val node = XmlNode(
                localName = normalizedName,
                attributes = buildMap {
                    for (index in 0 until attributes.length) {
                        val name = attributes.getLocalName(index)
                            .ifEmpty { attributes.getQName(index).substringAfter(':') }
                        put(name, attributes.getValue(index))
                    }
                },
                parent = stack.lastOrNull(),
                line = locator?.lineNumber,
                column = locator?.columnNumber,
                documentOrder = documentOrder++,
            )
            stack.lastOrNull()?.content?.add(XmlContent.Child(node))
            if (root == null) root = node
            stack.addLast(node)
        }

        override fun characters(ch: CharArray, start: Int, length: Int) {
            if (length > 0) stack.lastOrNull()?.content?.add(
                XmlContent.Text(
                    String(
                        ch,
                        start,
                        length
                    )
                )
            )
        }

        override fun endElement(uri: String, localName: String, qName: String) {
            if (stack.isNotEmpty()) stack.removeLast()
        }
    }

    private class XmlNode(
        val localName: String,
        private val attributes: Map<String, String>,
        val parent: XmlNode?,
        val line: Int?,
        val column: Int?,
        val documentOrder: Int,
    ) {
        val content = mutableListOf<XmlContent>()

        fun attribute(localName: String): String? =
            attributes.entries.firstOrNull { it.key.equals(localName, ignoreCase = true) }?.value

        fun hasTiming(): Boolean =
            attribute("begin") != null || attribute("end") != null || attribute("dur") != null

        fun descendants(): Sequence<XmlNode> = sequence {
            content.forEach { item ->
                if (item is XmlContent.Child) {
                    yield(item.node)
                    yieldAll(item.node.descendants())
                }
            }
        }

        fun fullText(): String = buildString {
            content.forEach { item ->
                when (item) {
                    is XmlContent.Text -> append(item.value)
                    is XmlContent.Child -> {
                        if (item.node.localName == "br") {
                            append(TTML_LINE_BREAK)
                        } else {
                            val childText = item.node.fullText()
                            val role = item.node.attribute("role")?.lowercase().orEmpty()
                            if (isNotEmpty() && !last().isWhitespace() && childText.isNotEmpty() && !childText.first().isWhitespace()) {
                                if (role.contains("bg") || childText.startsWith("(")) {
                                    append(' ')
                                }
                            }
                            append(childText)
                        }
                    }
                }
            }
        }
    }

    private sealed interface XmlContent {
        data class Text(val value: String) : XmlContent
        data class Child(val node: XmlNode) : XmlContent
    }

    private class SecurityConfigurationException(message: String, cause: Throwable) :
        Exception(message, cause)
}
