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
import xyz.tosdev.yetasimplelyicsapp.domain.lyrics.LyricsAlignment
import xyz.tosdev.yetasimplelyicsapp.domain.lyrics.LyricsCapability
import xyz.tosdev.yetasimplelyicsapp.domain.lyrics.LyricsDocument
import xyz.tosdev.yetasimplelyicsapp.domain.lyrics.LyricsLine
import xyz.tosdev.yetasimplelyicsapp.domain.lyrics.LyricsTextDirection
import xyz.tosdev.yetasimplelyicsapp.domain.lyrics.LyricsWord
import xyz.tosdev.yetasimplelyicsapp.domain.lyrics.LyricsWordSegment
import xyz.tosdev.yetasimplelyicsapp.domain.lyrics.SyncType
import xyz.tosdev.yetasimplelyicsapp.domain.lyrics.TimingQuality
import xyz.tosdev.yetasimplelyicsapp.domain.lyrics.hasReliableWordTiming
import xyz.tosdev.yetasimplelyicsapp.domain.lyrics.reconstructSemanticLyricsWords
import xyz.tosdev.yetasimplelyicsapp.domain.lyrics.semanticTextTokens
import java.io.StringReader
import javax.xml.XMLConstants
import javax.xml.parsers.SAXParserFactory
import kotlin.math.roundToLong

private const val TTML_LINE_BREAK = "\uE000"
private const val APPLE_MUSIC_LYRICS_NAMESPACE =
    "http://music.apple.com/lyric-ttml-internal"
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
        val appleTimingMode = root.appleMusicTimingMode()
        val resolver = TimingResolver(
            timingParameters,
            diagnostics,
            appleTimingMode != null,
        )
        val styleResolver = StyleResolver(root)
        val agentResolver = AgentResolver(root)
        val paragraphs = root.descendants().filter { it.localName == "body" }
            .flatMap { it.descendants() }.filter { it.localName == "p" }.toList()
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

        val drafts = paragraphs.flatMap { paragraph ->
            val background = paragraph.lyricDescendants().filter { it.isBackground() }.toList()
            val group = paragraph.explicitGroupId() ?: paragraph.attribute("key")
            ?: if (background.isNotEmpty()) "ttml-p-${paragraph.documentOrder}" else null
            val main = parseParagraph(
                paragraph,
                resolver,
                styleResolver,
                agentResolver,
                diagnostics,
            )?.copy(groupId = group)
            val backing = background.mapNotNull { node ->
                parseParagraph(node, resolver, styleResolver, agentResolver, diagnostics)?.copy(
                    groupId = group,
                    agentId = agentResolver.resolve(
                        node.firstInheritedAttribute("agent", "speaker", "voice")
                            ?: paragraph.firstInheritedAttribute("agent", "speaker", "voice"),
                    ),
                    isBackground = true,
                )
            }
            listOfNotNull(main) + backing
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
            val finalEnd = draft.endMs ?: draft.startMs?.let { start ->
                nextStart?.takeIf { it >= start }
            }
            val translations = sidecarText(root, draft, "translation") + auxiliary
                .filter { it.role == LineRole.TRANSLATION && matchesLine(draft, it) }
                .associate { (it.languageTag ?: "und") to it.text } + draft.translations
            val romanizations = sidecarText(root, draft, "transliteration") + auxiliary
                .filter { it.role == LineRole.ROMANIZATION && matchesLine(draft, it) }
                .associate { (it.languageTag ?: "und-Latn") to it.text } + draft.romanizations

            val words = reconstructSemanticLyricsWords(
                lineText = draft.text,
                languageTag = draft.languageTag ?: context.languageTag ?: root.attribute("lang"),
                words = draft.words.mapIndexed { wordIndex, word ->
                    val end = word.endMs
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
                        segments = word.segments.map { segment ->
                            LyricsWordSegment(
                                text = segment.text,
                                startTimeMs = segment.startMs,
                                endTimeMs = segment.endMs,
                                timingQuality = when {
                                    segment.explicitStart && segment.explicitEnd -> TimingQuality.EXPLICIT
                                    segment.startMs != null || segment.endMs != null -> TimingQuality.INFERRED
                                    else -> TimingQuality.UNKNOWN
                                },
                            )
                        },
                    )
                },
            )
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
                alignment = draft.alignment,
                role = draft.rawRole,
                region = draft.region,
                direction = draft.direction,
                isBackground = draft.isBackground,
                timingQuality = when {
                    draft.explicitStart && draft.explicitEnd -> TimingQuality.EXPLICIT
                    draft.startMs != null || finalEnd != null -> TimingQuality.INFERRED
                    else -> TimingQuality.UNKNOWN
                },
            )
        }

        val hasTimedLines = lines.any { it.startTimeMs != null }
        val hasReliableWordLines = lines.any(LyricsLine::hasReliableWordTiming)
        val hasLineOnlyForegroundTiming = lines.any { line ->
            !line.isBackground && line.startTimeMs != null && !line.hasReliableWordTiming
        }
        val capabilities = buildSet {
            add(LyricsCapability.PLAIN_TEXT)
            if (appleTimingMode != null) add(LyricsCapability.AMLL_TIMING)
            if (hasTimedLines) add(LyricsCapability.LINE_SYNC)
            if (hasReliableWordLines) add(LyricsCapability.WORD_SYNC)
            if (lines.any { it.text.translations.isNotEmpty() }) add(LyricsCapability.TRANSLATION)
            if (lines.any { it.text.romanizations.isNotEmpty() }) add(LyricsCapability.ROMANIZATION)
            if (lines.mapNotNull(LyricsLine::agentId)
                    .distinct().size > 1
            ) add(LyricsCapability.MULTI_AGENT)
        }
        val syncType = when {
            hasReliableWordLines && hasLineOnlyForegroundTiming -> SyncType.MIXED
            hasReliableWordLines -> SyncType.WORD
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
        styleResolver: StyleResolver,
        agentResolver: AgentResolver,
        diagnostics: MutableList<ParseDiagnostic>,
    ): LineDraft? {
        val paragraphText = if (paragraph.preservesXmlSpace()) {
            paragraph.lyricText().replace(TTML_LINE_BREAK, "\n")
        } else normalizeText(paragraph.lyricText())
        if (paragraphText.isBlank()) {
            diagnostics += diagnostic(
                ParseDiagnosticSeverity.INFO, "TTML_EMPTY_PARAGRAPH",
                "An empty TTML lyric element was ignored.", paragraph,
            )
            return null
        }

        val paragraphTiming = resolver.resolve(paragraph)
        // Generic TTML may expose valid word timing without Apple metadata. AMLL additionally
        // declares its exact timing mode through the Apple namespace; that mode controls only
        // AMLL-specific syllable aggregation and validation.
        val words = extractWords(paragraph, resolver, paragraphTiming)
        val text = words
            .flatMap(TimedWordDraft::segments)
            .joinToString(separator = "", transform = TimedSegmentDraft::text)
            .takeIf(String::isNotBlank)
            ?.replace(TTML_LINE_BREAK, "\n")
            ?.let { if (paragraph.preservesXmlSpace()) it else it.trim() }
            ?: paragraphText

        val explicitParagraphStart = paragraph.attribute("begin") != null
        val explicitParagraphEnd =
            paragraph.attribute("end") != null || paragraph.attribute("dur") != null
        val inheritedTiming =
            generateSequence(paragraph.parent) { it.parent }.any { it.hasTiming() }
        val lineStart = if (explicitParagraphStart) paragraphTiming.startMs
        else words.firstOrNull()?.startMs ?: paragraphTiming.startMs.takeIf { inheritedTiming }
        val lineEnd = if (explicitParagraphEnd) {
            val baseEnd = paragraphTiming.endMs
            val lastWordEnd = words.lastOrNull()?.endMs
            if (baseEnd != null && lastWordEnd != null) maxOf(baseEnd, lastWordEnd) else baseEnd
                ?: lastWordEnd
        } else words.lastOrNull()?.endMs ?: paragraphTiming.endMs.takeIf { inheritedTiming }
        val rawRole = paragraph.roleTokens().joinToString(" ").ifEmpty { null }
        val roleValue = rawRole.orEmpty().lowercase()
        val role = when {
            "translat" in roleValue -> LineRole.TRANSLATION
            "roman" in roleValue || "translit" in roleValue -> LineRole.ROMANIZATION
            else -> LineRole.PRIMARY
        }
        val direction = styleResolver.direction(paragraph)
        val isBackground = paragraph.isBackground()
        // TTML permits the singer on the vocal span, including inside an untimed wrapper.
        // Choose actual vocal text by time, never a translation/background/formatting node.
        val firstVocalSpan = paragraph.lyricDescendants().filter { candidate ->
            candidate.localName == "span" && !candidate.isAuxiliary() &&
                !candidate.isBackground() && !candidate.isRubyAnnotation() &&
                candidate.content.any { it is XmlContent.Text && it.value.any(Char::isLetterOrDigit) }
        }.minByOrNull { resolver.resolve(it).startMs }
        val agentReference = firstVocalSpan?.firstInheritedAttribute("agent", "speaker", "voice")
            ?: paragraph.firstInheritedAttribute("agent", "speaker", "voice")
        val agentId = agentResolver.resolve(agentReference)
        val sourceAlignment = styleResolver.alignment(paragraph, direction)
        val alignment = if (sourceAlignment == LyricsAlignment.AUTO && !isBackground) {
            agentResolver.defaultAlignment(agentReference) ?: LyricsAlignment.AUTO
        } else {
            sourceAlignment
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
            languageTag = paragraph.inheritedAttribute("lang"),
            agentId = agentId,
            groupId = paragraph.explicitGroupId() ?: paragraph.attribute("key"),
            sourceKey = paragraph.attribute("key"),
            alignment = alignment,
            rawRole = rawRole,
            region = styleResolver.regionIdentity(paragraph),
            direction = direction,
            isBackground = isBackground,
            translations = inlineText(paragraph, LineRole.TRANSLATION),
            romanizations = inlineText(paragraph, LineRole.ROMANIZATION),
        )
    }

    private fun inlineText(node: XmlNode, role: LineRole): Map<String, String> =
        node.lyricDescendants().filter { it.lineRole() == role }
            .associate { child ->
                (child.attribute("lang")
                    ?: if (role == LineRole.ROMANIZATION) "und-Latn" else "und") to
                        normalizeText(child.lyricText())
            }.filterValues { it.isNotBlank() }

    private fun sidecarText(root: XmlNode, draft: LineDraft, kind: String): Map<String, String> {
        if (draft.isBackground || draft.sourceKey == null) return emptyMap()
        return root.descendants().filter { it.localName == "head" }
            .flatMap { it.descendants() }.filter { it.localName == kind }
            .mapNotNull { container ->
                val text = container.descendants().filter {
                    it.localName == "text" && it.attribute("for") == draft.sourceKey
                }.joinToString(" ") { normalizeText(it.lyricText()) }
                if (text.isBlank()) null else
                    (container.attribute("lang")
                        ?: if (kind == "translation") "und" else "und-Latn") to text
            }.toMap()
    }

    private fun extractWords(
        node: XmlNode,
        resolver: TimingResolver,
        paragraphTiming: Timing,
    ): List<TimedWordDraft> {
        val segments = mutableListOf<TimedSegmentDraft>()
        var prefix = ""
        val appleWordTiming = node.appleMusicTimingMode() == AppleMusicTimingMode.WORD
        fun plain(value: String, preserveSpace: Boolean = node.preservesXmlSpace()) {
            // Pretty-printed TTML adds indentation nodes between spans. They are formatting, not
            // lyric separators. A literal inline space remains meaningful and separates words.
            if (!preserveSpace && value.isBlank() && (value.contains('\n') || value.contains('\r'))) return
            val text = if (preserveSpace) value else value.normalizeInlineSourceWhitespace()
            if (segments.isEmpty()) prefix += text
            else segments[segments.lastIndex] = segments.last().copy(text = segments.last().text + text)
        }

        fun visit(parent: XmlNode) {
            parent.content.forEach { item ->
                when (item) {
                    is XmlContent.Text -> plain(item.value, parent.preservesXmlSpace())
                    is XmlContent.Child -> {
                        val child = item.node
                        if (child.isAuxiliary() || child.isBackground() || child.isRubyAnnotation()) return@forEach
                        if (child.localName == "br") {
                            plain(TTML_LINE_BREAK)
                        } else if (child.localName == "span" && child.hasTiming() &&
                            child.lyricDescendants().none {
                                it.localName == "span" && it.hasTiming() &&
                                        !it.isAuxiliary() && !it.isBackground() && !it.isRubyAnnotation()
                            }
                        ) {
                            val rawText = child.lyricText()
                            val text = if (child.preservesXmlSpace()) rawText else rawText.normalizeInlineSourceWhitespace()
                            if (text.isNotBlank()) {
                                val timing = resolver.resolve(child)
                                val explicitStart = child.attribute("begin") != null
                                val explicitEnd = child.attribute("end") != null ||
                                    child.attribute("dur") != null
                                val isValidAppleWordSpan = !appleWordTiming || (
                                    explicitStart &&
                                        explicitEnd &&
                                        timing.endMs != null &&
                                        timing.endMs > timing.startMs &&
                                        timing.startMs >= paragraphTiming.startMs &&
                                        (paragraphTiming.endMs == null ||
                                            timing.endMs <= paragraphTiming.endMs)
                                    )
                                if (isValidAppleWordSpan) {
                                    segments += TimedSegmentDraft(
                                        text = prefix + text,
                                        startMs = timing.startMs,
                                        endMs = timing.endMs,
                                        explicitStart = explicitStart,
                                        explicitEnd = explicitEnd,
                                        partOfWord = child.partOfWord(),
                                        structuralWordOwner = child.structuralWordOwner(node),
                                    )
                                }
                                prefix = ""
                            }
                        } else visit(child)
                    }
                }
            }
        }
        visit(node)
        // Keep separators on word spans, including spaces BETWEEN XML elements.
        if (segments.isNotEmpty() && !node.preservesXmlSpace()) {
            segments[0] = segments.first().copy(text = segments.first().text.trimStart())
            segments[segments.lastIndex] = segments.last().copy(text = segments.last().text.trimEnd())
        }
        return aggregateWords(
            segments = segments.map {
                it.copy(text = it.text.replace(TTML_LINE_BREAK, "\n"))
            },
            amllWordTiming = appleWordTiming,
            languageTag = node.inheritedAttribute("lang"),
        )
    }

    private fun aggregateWords(
        segments: List<TimedSegmentDraft>,
        amllWordTiming: Boolean,
        languageTag: String?,
    ): List<TimedWordDraft> {
        if (segments.isEmpty()) return emptyList()
        val joinedText = segments.joinToString(separator = "") { it.text }
        val semanticBoundaries = semanticTextTokens(joinedText, languageTag)
            .mapTo(mutableSetOf()) { it.endIndex }
        val groups = mutableListOf<MutableList<TimedSegmentDraft>>()
        var previousEndOffset = 0
        segments.forEachIndexed { index, segment ->
            val previous = segments.getOrNull(index - 1)
            val joinsPrevious = previous != null && when {
                previous.partOfWord == true -> true
                previous.partOfWord == false -> false
                segment.partOfWord == true -> true
                segment.startsWithWhitespace() || previous.endsWithWhitespace() -> false
                previous.structuralWordOwner != null &&
                    previous.structuralWordOwner == segment.structuralWordOwner -> true
                segment.isPunctuationOnly() -> true
                previous.isOpeningPunctuationOnly() -> true
                previous.endsWithWordConnector() -> true
                previous.hasLexicalContent() && segment.hasLexicalContent() ->
                    previousEndOffset !in semanticBoundaries
                else -> false
            }
            if (!joinsPrevious) groups.add(mutableListOf())
            groups.last() += segment
            previousEndOffset += segment.text.length
        }
        return groups.map { group ->
            TimedWordDraft(
                text = group.joinToString("") { it.text }.let { text ->
                    if (amllWordTiming) text.trim() else text
                },
                startMs = group.first().startMs,
                endMs = group.last().endMs,
                explicitStart = group.first().explicitStart,
                explicitEnd = group.last().explicitEnd,
                segments = group,
            )
        }
    }

    private fun matchesLine(primary: LineDraft, auxiliary: LineDraft): Boolean {
        if (primary.isBackground) return false
        if (primary.sourceKey != null && auxiliary.sourceKey != null) {
            return primary.sourceKey == auxiliary.sourceKey
        }
        if (primary.groupId != null && auxiliary.groupId != null) {
            return primary.groupId == auxiliary.groupId
        }
        if (primary.agentId != null && auxiliary.agentId != null && primary.agentId != auxiliary.agentId) return false
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

    private fun XmlNode.preservesXmlSpace(): Boolean =
        inheritedAttribute(XMLConstants.XML_NS_URI, "space") == "preserve"

    private fun String.normalizeInlineSourceWhitespace(): String =
        replace(Regex("""[ \t]*[\r\n]+[ \t]*"""), " ")

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
        val sourceKey: String?,
        val alignment: LyricsAlignment,
        val rawRole: String?,
        val region: String?,
        val direction: LyricsTextDirection,
        val isBackground: Boolean = false,
        val translations: Map<String, String> = emptyMap(),
        val romanizations: Map<String, String> = emptyMap(),
    )

    private data class TimedWordDraft(
        val text: String,
        val startMs: Long?,
        val endMs: Long?,
        val explicitStart: Boolean,
        val explicitEnd: Boolean,
        val segments: List<TimedSegmentDraft>,
    )

    private data class TimedSegmentDraft(
        val text: String,
        val startMs: Long?,
        val endMs: Long?,
        val explicitStart: Boolean,
        val explicitEnd: Boolean,
        val partOfWord: Boolean?,
        val structuralWordOwner: Int?,
    ) {
        fun startsWithWhitespace() = text.firstOrNull()?.isWhitespace() == true
        fun endsWithWhitespace() = text.lastOrNull()?.isWhitespace() == true
        fun isPunctuationOnly() =
            text.isNotEmpty() && text.all { it.isWhitespace() || !it.isLetterOrDigit() }

        fun isOpeningPunctuationOnly() =
            text.trim().isNotEmpty() && text.trim().all { it in OPENING_PUNCTUATION }

        fun endsWithWordConnector() = text.trimEnd().lastOrNull() in WORD_CONNECTORS

        fun hasLexicalContent() = text.any(Char::isLetterOrDigit)
    }

    private data class Timing(
        val startMs: Long,
        val endMs: Long?,
    )

    private data class ParsedTime(
        val milliseconds: Long,
        val isClockTime: Boolean,
    )

    private enum class AppleMusicTimingMode {
        WORD,
        LINE,
    }

    private data class XmlAttribute(
        val namespaceUri: String,
        val localName: String,
        val value: String,
    )

    private class StyleResolver(root: XmlNode) {
        private val styles = root.descendants()
            .filter { it.localName == "style" }
            .mapNotNull { node -> node.attribute("id")?.let { it to node } }
            .toMap()
        private val regions = root.descendants()
            .filter { it.localName == "region" }
            .mapNotNull { node -> node.attribute("id")?.let { it to node } }
            .toMap()

        fun direction(node: XmlNode): LyricsTextDirection {
            val value = resolvedAttribute(node, "direction")
                ?: resolvedAttribute(node, "writingMode")
            return when {
                value.equals("rtl", ignoreCase = true) || value.orEmpty().startsWith("rl", true) ->
                    LyricsTextDirection.RTL

                value.equals("ltr", ignoreCase = true) || value.orEmpty().startsWith("lr", true) ->
                    LyricsTextDirection.LTR

                else -> LyricsTextDirection.AUTO
            }
        }

        fun alignment(node: XmlNode, direction: LyricsTextDirection): LyricsAlignment =
            when (resolvedAttribute(node, "textAlign")?.lowercase()) {
                "left" -> LyricsAlignment.LEFT
                "right" -> LyricsAlignment.RIGHT
                "center" -> LyricsAlignment.CENTER
                "start" -> if (direction == LyricsTextDirection.RTL) LyricsAlignment.RIGHT else LyricsAlignment.LEFT
                "end" -> if (direction == LyricsTextDirection.RTL) LyricsAlignment.LEFT else LyricsAlignment.RIGHT
                else -> LyricsAlignment.AUTO
            }

        fun regionIdentity(node: XmlNode): String? {
            val reference = node.inheritedAttribute("region")?.normalizeReference() ?: return null
            return regions[reference]?.attribute("identifier")?.takeIf(String::isNotBlank)
                ?: reference
        }

        private fun resolvedAttribute(node: XmlNode, name: String): String? {
            var current: XmlNode? = node
            while (current != null) {
                current.attribute(name)?.let { return it }
                resolveStyles(current.attribute("style"), name, mutableSetOf())?.let { return it }
                current = current.parent
            }
            node.inheritedAttribute("region")?.normalizeReference()?.let { regionId ->
                regions[regionId]?.let { region ->
                    region.attribute(name)?.let { return it }
                    resolveStyles(
                        region.attribute("style"),
                        name,
                        mutableSetOf()
                    )?.let { return it }
                }
            }
            return null
        }

        private fun resolveStyles(
            references: String?,
            name: String,
            visited: MutableSet<String>,
        ): String? {
            references.orEmpty().trim().split(Regex("""\s+"""))
                .filter(String::isNotBlank)
                .asReversed()
                .forEach { rawStyleId ->
                    val styleId = rawStyleId.normalizeReference()
                    if (!visited.add(styleId)) return@forEach
                    val style = styles[styleId] ?: return@forEach
                    style.attribute(name)?.let { return it }
                    resolveStyles(style.attribute("style"), name, visited)?.let { return it }
                }
            return null
        }

        private fun String.normalizeReference(): String = removePrefix("#")
    }

    private class AgentResolver(root: XmlNode) {
        private val definitions = root.descendants()
            .filter { it.localName == "agent" }
            .mapNotNull { node ->
                node.attribute("id")?.normalizeReference()?.let { id ->
                    AgentDefinition(
                        id = id,
                        identity = node.attribute("identifier")?.takeIf(String::isNotBlank) ?: id,
                    )
                }
            }
            .toList()
        private val aliases = definitions.associate { it.id to it.identity }
        private val order = definitions.mapIndexed { index, definition -> definition.id to index }.toMap()

        fun resolve(reference: String?): String? = reference.agentReferences()
            .takeIf(List<String>::isNotEmpty)
            ?.joinToString(separator = " ") { aliases[it] ?: it }

        fun defaultAlignment(reference: String?): LyricsAlignment? {
            val references = reference.agentReferences()
            if (references.size != 1) return null
            return when (order[references.single()]) {
                0 -> LyricsAlignment.LEFT
                1 -> LyricsAlignment.RIGHT
                null -> null
                else -> if ((order.getValue(references.single()) and 1) == 0) {
                    LyricsAlignment.LEFT
                } else {
                    LyricsAlignment.RIGHT
                }
            }
        }

        private fun String?.agentReferences(): List<String> = this.orEmpty()
            .trim()
            .split(Regex("""\s+"""))
            .filter(String::isNotBlank)
            .map { it.normalizeReference() }

        private fun String.normalizeReference(): String = removePrefix("#")

        private data class AgentDefinition(val id: String, val identity: String)
    }

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
        private val amllTiming: Boolean,
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
            SHORT_CLOCK_TIME.matchEntire(raw)?.let { match ->
                val minutes = match.groupValues[1].toLongOrNull() ?: return invalidTime(raw, node)
                val seconds = match.groupValues[2].toDoubleOrNull() ?: return invalidTime(raw, node)
                if (seconds >= 60.0) return invalidTime(raw, node)
                return ParsedTime(minutes * 60_000L + (seconds * 1_000.0).roundToLong(), true)
            }
            SECONDS_TIME.matchEntire(raw)?.let {
                val seconds = raw.toDoubleOrNull() ?: return invalidTime(raw, node)
                if (!seconds.isFinite() || seconds > Long.MAX_VALUE / 1_000.0) return invalidTime(
                    raw,
                    node
                )
                return ParsedTime((seconds * 1_000.0).roundToLong(), true)
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
                return ParsedTime(milliseconds.roundToLong(), isClockTime = amllTiming)
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
            private val SHORT_CLOCK_TIME = Regex("""(\d+):(\d{2}(?:\.\d+)?)""")
            private val SECONDS_TIME = Regex("""\d+(?:\.\d+)?""")
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
                attributes = buildList {
                    for (index in 0 until attributes.length) {
                        val name = attributes.getLocalName(index)
                            .ifEmpty { attributes.getQName(index).substringAfter(':') }
                        add(
                            XmlAttribute(
                                namespaceUri = attributes.getURI(index).orEmpty(),
                                localName = name,
                                value = attributes.getValue(index),
                            )
                        )
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
        private val attributes: List<XmlAttribute>,
        val parent: XmlNode?,
        val line: Int?,
        val column: Int?,
        val documentOrder: Int,
    ) {
        val content = mutableListOf<XmlContent>()

        fun attribute(localName: String): String? =
            attributes.firstOrNull { it.localName.equals(localName, ignoreCase = true) }?.value

        fun attribute(namespaceUri: String, localName: String): String? = attributes
            .firstOrNull {
                it.namespaceUri == namespaceUri &&
                    it.localName.equals(localName, ignoreCase = true)
            }
            ?.value

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

        fun inheritedAttribute(name: String): String? =
            attribute(name) ?: parent?.inheritedAttribute(name)

        fun inheritedAttribute(namespaceUri: String, localName: String): String? =
            attribute(namespaceUri, localName)
                ?: parent?.inheritedAttribute(namespaceUri, localName)

        fun appleMusicTimingMode(): AppleMusicTimingMode? =
            when (
                inheritedAttribute(APPLE_MUSIC_LYRICS_NAMESPACE, "timing")
                    ?.trim()
                    ?.lowercase()
            ) {
                "word" -> AppleMusicTimingMode.WORD
                "line" -> AppleMusicTimingMode.LINE
                else -> null
            }

        fun firstInheritedAttribute(vararg names: String): String? =
            names.firstNotNullOfOrNull(::attribute) ?: parent?.firstInheritedAttribute(*names)

        fun roleTokens(): List<String> = sequenceOf(attribute("role"), attribute("type"))
            .filterNotNull()
            .flatMap { it.trim().split(Regex("""\s+""")).asSequence() }
            .filter(String::isNotBlank)
            .distinct()
            .toList()

        fun explicitGroupId(): String? = attribute("group") ?: attribute("duet")

        fun partOfWord(): Boolean? = firstAttribute(
            "isPartOfWord",
            "partOfWord",
            "part-of-word",
            "wordPart",
            "isWordPart",
        )?.let { value ->
            when (value.trim().lowercase()) {
                "true", "1", "yes", "y" -> true
                "false", "0", "no", "n" -> false
                else -> null
            }
        }

        fun structuralWordOwner(paragraph: XmlNode): Int? = generateSequence(parent) { it.parent }
            .takeWhile { it !== paragraph }
            .firstOrNull { it.localName == "span" && !it.hasTiming() }
            ?.documentOrder

        private fun firstAttribute(vararg names: String): String? =
            names.firstNotNullOfOrNull(::attribute)

        fun lineRole(): LineRole {
            val role = roleTokens().joinToString(" ").lowercase()
            return when {
                "translat" in role -> LineRole.TRANSLATION
                "roman" in role || "translit" in role -> LineRole.ROMANIZATION
                else -> LineRole.PRIMARY
            }
        }

        fun isAuxiliary() = lineRole() != LineRole.PRIMARY
        fun isBackground() = roleTokens().any { token ->
            token.equals("x-bg", true) ||
                    token.equals("bg", true) ||
                    token.equals("background", true) ||
                    token.equals("backing", true) ||
                    token.equals("background-vocals", true)
        }

        fun isRubyAnnotation() = attribute("ruby").orEmpty().lowercase() in
                setOf("text", "textcontainer", "delimiter")

        // Enumerate annotation roots but never leak their children into the main vocal.
        fun lyricDescendants(): Sequence<XmlNode> = sequence {
            content.filterIsInstance<XmlContent.Child>().forEach { item ->
                yield(item.node)
                if (!item.node.isAuxiliary() && !item.node.isBackground() && !item.node.isRubyAnnotation()) {
                    yieldAll(item.node.lyricDescendants())
                }
            }
        }

        fun lyricText(): String = buildString {
            content.forEach { item ->
                when (item) {
                    is XmlContent.Text -> append(item.value)
                    is XmlContent.Child -> when {
                        item.node.isAuxiliary() || item.node.isBackground() || item.node.isRubyAnnotation() -> Unit
                        item.node.localName == "br" -> append(TTML_LINE_BREAK)
                        else -> append(item.node.lyricText())
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

    private companion object {
        val WORD_CONNECTORS = setOf('\'', '\u2019', '\u02BC', '-', '\u2010', '\u2011')
        val OPENING_PUNCTUATION = setOf('¿', '¡', '(', '[', '{', '«', '“', '‘')
    }
}
