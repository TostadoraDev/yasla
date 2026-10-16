package xyz.tosdev.yetasimplelyicsapp.domain.lyrics.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.tosdev.yetasimplelyicsapp.domain.lyrics.LyricsAlignment
import xyz.tosdev.yetasimplelyicsapp.domain.lyrics.LyricsCapability
import xyz.tosdev.yetasimplelyicsapp.domain.lyrics.LyricsSource
import xyz.tosdev.yetasimplelyicsapp.domain.lyrics.LyricsTextDirection
import xyz.tosdev.yetasimplelyicsapp.domain.lyrics.SyncType
import xyz.tosdev.yetasimplelyicsapp.domain.lyrics.hasReliableWordSync

class TtmlLyricsParserTest {
    private val parser = TtmlLyricsParser()
    private val context = LyricsParseContext(
        documentId = "ttml-doc",
        trackKey = "artist|title",
        source = LyricsSource(providerId = "test"),
    )

    @Test fun realRepeatedSpacesSurviveInLineTextAndTimingSegments() {
        val result = parser.parse("""<tt xmlns="http://www.w3.org/ns/ttml"><body><div><p begin="0s" end="2s"><span begin="0s" end="0.5s">te   </span><span begin="0.5s" end="1s">quiero</span></p></div></body></tt>""", context)
        val line = requireNotNull(result.document).lines.single()
        assertEquals("te   quiero", line.text.original)
        assertEquals(listOf("te", "quiero"), line.words.map { it.text })
        assertEquals("te   ", line.words.first().segments.single().text)
    }

    @Test fun xmlSpacePreserveKeepsEvenLeadingAndTrailingSourceSpaces() {
        val result = parser.parse("""<tt xmlns="http://www.w3.org/ns/ttml"><body><div><p xml:space="preserve" begin="0s" end="2s"><span begin="0s" end="0.5s"> te  </span><span begin="0.5s" end="1s">quiero </span></p></div></body></tt>""", context)
        val line = requireNotNull(result.document).lines.single()
        assertEquals(" te  quiero ", line.text.original)
        assertEquals(" te  ", line.words.first().segments.single().text)
        assertEquals("quiero ", line.words.last().segments.single().text)
    }

    @Test fun firstSecondarySingerCanBeAssignedOnTheVocalSpan() {
        val result = parser.parse("""<tt xmlns="http://www.w3.org/ns/ttml" xmlns:ttm="http://www.w3.org/ns/ttml#metadata"><head><metadata><ttm:agent xml:id="v1" type="person"/><ttm:agent xml:id="v2" type="person"/></metadata></head><body><div><p begin="0s" end="2s"><span ttm:agent="v2" begin="0s" end="1s">quiero</span></p></div></body></tt>""", context)
        val first = requireNotNull(result.document).lines.first()
        assertEquals("v2", first.agentId)
        assertEquals(xyz.tosdev.yetasimplelyicsapp.domain.lyrics.LyricsAlignment.RIGHT, first.alignment)
    }

    @Test
    fun namespacedTtmlParsesParagraphsDurationsWordsAndAgents() {
        val result = parser.parse(
            """
            <tt xmlns="http://www.w3.org/ns/ttml"
                xmlns:ttp="http://www.w3.org/ns/ttml#parameter"
                xmlns:ttm="http://www.w3.org/ns/ttml#metadata"
                ttp:frameRate="25"
                xml:lang="en">
              <body>
                <div>
                  <p begin="00:00:10.000" dur="2s" ttm:agent="v1">
                    <span begin="00:00:10.000" end="00:00:10.500">Hello </span>
                    <span begin="00:00:10.500" dur="0.5s">world</span>
                  </p>
                </div>
              </body>
            </tt>
            """.trimIndent(),
            context,
        )

        assertTrue(result.isSuccess)
        val document = requireNotNull(result.document)
        assertEquals("en", document.languageTag)
        assertEquals(SyncType.WORD, document.syncType)
        assertEquals(10_000L, document.lines.single().startTimeMs)
        assertEquals(12_000L, document.lines.single().endTimeMs)
        assertEquals("v1", document.lines.single().agentId)
        assertEquals("Hello world", document.lines.single().text.original)
        assertEquals(listOf(10_000L, 10_500L), document.lines.single().words.map { it.startTimeMs })
        assertEquals(listOf(10_500L, 11_000L), document.lines.single().words.map { it.endTimeMs })
        assertTrue(LyricsCapability.WORD_SYNC in document.capabilities)
    }

    @Test
    fun offsetTimesAreRelativeToTimedAncestors() {
        val result = parser.parse(
            """
            <tt xmlns="http://www.w3.org/ns/ttml">
              <body>
                <div begin="2s">
                  <p begin="1s" dur="2s">
                    <span begin="0.5s" dur="0.5s">Nested</span>
                  </p>
                </div>
              </body>
            </tt>
            """.trimIndent(),
            context,
        )

        val line = requireNotNull(result.document).lines.single()
        assertEquals(3_000L, line.startTimeMs)
        assertEquals(5_000L, line.endTimeMs)
        assertEquals(3_500L, line.words.single().startTimeMs)
        assertEquals(4_000L, line.words.single().endTimeMs)
    }

    @Test
    fun translationAndRomanizationParagraphsMergeByKey() {
        val result = parser.parse(
            """
            <tt xmlns="http://www.w3.org/ns/ttml"
                xmlns:ttm="http://www.w3.org/ns/ttml#metadata"
                xmlns:amll="urn:amll">
              <body><div>
                <p begin="1s" amll:key="line-1">你好</p>
                <p begin="1s" amll:key="line-1" ttm:role="translation" xml:lang="es">Hola</p>
                <p begin="1s" amll:key="line-1" ttm:role="romanization" xml:lang="zh-Latn">ni hao</p>
              </div></body>
            </tt>
            """.trimIndent(),
            context,
        )

        val document = requireNotNull(result.document)
        val line = document.lines.single()
        assertEquals("Hola", line.text.translations["es"])
        assertEquals("ni hao", line.text.romanizations["zh-Latn"])
        assertTrue(LyricsCapability.TRANSLATION in document.capabilities)
        assertTrue(LyricsCapability.ROMANIZATION in document.capabilities)
    }

    @Test
    fun frameAndTickTimingUseRootParameters() {
        val result = parser.parse(
            """
            <tt xmlns="http://www.w3.org/ns/ttml"
                xmlns:ttp="http://www.w3.org/ns/ttml#parameter"
                ttp:frameRate="25"
                ttp:tickRate="100">
              <body><div>
                <p begin="00:00:01:12" dur="50t">Frame line</p>
              </div></body>
            </tt>
            """.trimIndent(),
            context,
        )

        val line = requireNotNull(result.document).lines.single()
        assertEquals(1_480L, line.startTimeMs)
        assertEquals(1_980L, line.endTimeMs)
    }

    @Test
    fun malformedXmlReturnsStructuredLocation() {
        val result = parser.parse("<tt><body><p>broken</body></tt>", context)

        assertFalse(result.isSuccess)
        assertNull(result.document)
        assertEquals("TTML_MALFORMED_XML", result.diagnostics.single().code)
        assertTrue((result.diagnostics.single().line ?: 0) > 0)
    }

    @Test
    fun doctypeAndExternalEntitiesAreRejected() {
        val result = parser.parse(
            """
            <!DOCTYPE tt [<!ENTITY xxe SYSTEM "file:///etc/passwd">]>
            <tt xmlns="http://www.w3.org/ns/ttml"><body><p>&xxe;</p></body></tt>
            """.trimIndent(),
            context,
        )

        assertFalse(result.isSuccess)
        assertNull(result.document)
        assertTrue(result.diagnostics.any { it.severity == ParseDiagnosticSeverity.ERROR })
    }

    @Test
    fun unsupportedTimeProducesWarningInsteadOfCrashing() {
        val result = parser.parse(
            """
            <tt xmlns="http://www.w3.org/ns/ttml">
              <body><p begin="wallclock(2026-01-01T00:00:00Z)">Text</p></body>
            </tt>
            """.trimIndent(),
            context,
        )

        assertTrue(result.document != null)
        assertTrue(result.diagnostics.any { it.code == "TTML_INVALID_TIME" })
    }

    @Test
    fun amllShortAndBareTimestampsStayAbsoluteInsideTimedAncestors() {
        val result = parser.parse(
            """
            <tt xmlns="http://www.w3.org/ns/ttml" xmlns:itunes="http://music.apple.com/lyric-ttml-internal" itunes:timing="Word">
              <body dur="01:00.000"><div begin="00:20.000" end="00:50.000">
                <p begin="00:24.000" end="28.500"><span begin="24.000" end="00:26.000">Hola</span> <span begin="26.000" end="28.500">mundo</span></p>
              </div></body>
            </tt>
        """.trimIndent(), context
        )
        assertTrue(result.diagnostics.toString(), result.isSuccess)
        assertTrue(result.diagnostics.toString(), result.diagnostics.isEmpty())
        val line = requireNotNull(result.document).lines.single()
        assertEquals(24_000L, line.startTimeMs)
        assertEquals(28_500L, line.endTimeMs)
        assertEquals(listOf(24_000L, 26_000L), line.words.map { it.startTimeMs })
        assertEquals(listOf("Hola", "mundo"), line.words.map { it.text })
        assertEquals(line.text.original, line.words.joinToString(" ") { it.text })
    }

    @Test
    fun amllAuxiliarySpansAndBackingVocalsRemainSeparate() {
        val result = parser.parse(
            """
            <tt xmlns="http://www.w3.org/ns/ttml" xmlns:ttm="http://www.w3.org/ns/ttml#metadata"
                xmlns:itunes="http://music.apple.com/lyric-ttml-internal" itunes:timing="Word" xml:lang="ja">
              <body><div>
                <p begin="00:24.000" end="00:26.000" itunes:key="L1" ttm:agent="v1"><span begin="00:24.000" end="00:25.000">こん</span><span begin="00:25.000" end="00:26.000">にちは</span><span ttm:role="x-translation" xml:lang="es">Hola</span><span ttm:role="x-roman" xml:lang="ja-Latn">konnichiwa</span><span ttm:role="x-bg" begin="00:27.000" end="00:28.500"><span begin="00:27.000" end="00:28.500">(やあ)</span><span ttm:role="x-translation" xml:lang="es">Hey</span><span ttm:role="x-roman">yaa</span></span></p>
                <p begin="00:29.000" end="00:30.000" itunes:key="L2" ttm:agent="v2">Adiós</p>
              </div></body>
            </tt>
        """.trimIndent(), context
        )
        val document = requireNotNull(result.document)
        assertEquals(3, document.lines.size)
        val main = document.lines[0]
        val bg = document.lines[1]
        assertEquals("こんにちは", main.text.original)
        assertEquals("Hola", main.text.translations["es"])
        assertEquals("konnichiwa", main.text.romanizations["ja-Latn"])
        assertEquals(26_000L, main.endTimeMs)
        assertFalse(main.isBackground)
        assertEquals("(やあ)", bg.text.original)
        assertEquals("Hey", bg.text.translations["es"])
        assertEquals("yaa", bg.text.romanizations["und-Latn"])
        assertEquals(27_000L, bg.startTimeMs)
        assertEquals(28_500L, bg.endTimeMs)
        assertTrue(bg.isBackground)
        assertEquals(main.groupId, bg.groupId)
        assertEquals("v1", bg.agentId)
        assertTrue(LyricsCapability.MULTI_AGENT in document.capabilities)
    }

    @Test
    fun headSidecarsAttachByKeyWithoutDuplicatingBodyLyrics() {
        val result = parser.parse(
            """
            <tt xmlns="http://www.w3.org/ns/ttml" xmlns:itunes="http://music.apple.com/lyric-ttml-internal">
              <head><metadata><itunes:iTunesMetadata><itunes:translations>
                <itunes:translation xml:lang="es" type="subtitle"><itunes:text for="L1">Uno</itunes:text><itunes:text for="L2">Dos</itunes:text></itunes:translation>
              </itunes:translations><itunes:transliterations>
                <itunes:transliteration xml:lang="ja-Latn"><itunes:text for="L1">ichi</itunes:text></itunes:transliteration>
              </itunes:transliterations></itunes:iTunesMetadata></metadata></head>
              <body><div><p begin="01:00.250" end="01:01.000" itunes:key="L1">一</p><p begin="01:00.250" end="01:01.000" itunes:key="L2">二</p></div></body>
            </tt>
        """.trimIndent(), context
        )
        val lines = requireNotNull(result.document).lines
        assertEquals(2, lines.size)
        assertEquals(60_250L, lines[0].startTimeMs)
        assertEquals(mapOf("es" to "Uno"), lines[0].text.translations)
        assertEquals(mapOf("es" to "Dos"), lines[1].text.translations)
        assertEquals(mapOf("ja-Latn" to "ichi"), lines[0].text.romanizations)
        assertTrue(lines[1].text.romanizations.isEmpty())
    }

    @Test
    fun sameTimeDuetsDoNotShareTranslationsFromDifferentKeys() {
        val result = parser.parse(
            """
            <tt xmlns:ttm="http://www.w3.org/ns/ttml#metadata" xmlns:itunes="http://music.apple.com/lyric-ttml-internal">
              <body><div>
                <p begin="00:01.000" itunes:key="L1" ttm:agent="v1">One</p>
                <p begin="00:01.000" itunes:key="L2" ttm:agent="v2">Two</p>
                <p begin="00:01.000" itunes:key="L1" ttm:role="translation" xml:lang="es">Uno</p>
                <p begin="00:01.000" itunes:key="L2" ttm:role="translation" xml:lang="es">Dos</p>
              </div></body>
            </tt>
        """.trimIndent(), context
        )
        val lines = requireNotNull(result.document).lines
        assertEquals(2, lines.size)
        assertEquals("Uno", lines[0].text.translations["es"])
        assertEquals("Dos", lines[1].text.translations["es"])
    }

    @Test
    fun lineTimingModeDoesNotInventWordSynchronization() {
        val result = parser.parse(
            """
            <tt xmlns:itunes="http://music.apple.com/lyric-ttml-internal" itunes:timing="Line">
              <body><div begin="10s"><p begin="12s" end="15s"><span>Line</span></p></div></body>
            </tt>
        """.trimIndent(), context
        )
        val document = requireNotNull(result.document)
        assertEquals(SyncType.LINE, document.syncType)
        assertEquals(12_000L, document.lines.single().startTimeMs)
        assertEquals(15_000L, document.lines.single().endTimeMs)
        assertTrue(document.lines.single().words.isEmpty())
        assertFalse(document.hasReliableWordSync)
    }

    @Test
    fun validInnerTimingOverridesIncorrectLineModeHint() {
        val result = parser.parse(
            """
            <tt xmlns="http://www.w3.org/ns/ttml"
                xmlns:itunes="http://music.apple.com/lyric-ttml-internal"
                itunes:timing="Line">
              <body><p begin="1s" end="2s">
                <span begin="1s" end="1.4s">hola </span><span begin="1.4s" end="2s">mundo</span>
              </p></body>
            </tt>
            """.trimIndent(),
            context,
        )

        val document = requireNotNull(result.document)
        assertEquals(SyncType.WORD, document.syncType)
        assertTrue(document.hasReliableWordSync)
        assertEquals(listOf("hola", "mundo"), document.lines.single().words.map { it.text })
        assertEquals(
            listOf("hola ", "mundo"),
            document.lines.single().words.flatMap { it.segments }.map { it.text },
        )
    }

    @Test
    fun untimedBackgroundInheritsItsParagraphInterval() {
        val result = parser.parse(
            """
            <tt xmlns:ttm="http://www.w3.org/ns/ttml#metadata"><body><p begin="00:10.000" end="00:12.000">Hola<span ttm:role="x-bg">(eco)</span></p></body></tt>
        """.trimIndent(), context
        )
        val background = requireNotNull(result.document).lines.single { it.isBackground }
        assertEquals("(eco)", background.text.original)
        assertEquals(10_000L, background.startTimeMs)
        assertEquals(12_000L, background.endTimeMs)
        assertTrue(background.words.isEmpty())
    }

    @Test
    fun rubyReadingsAreNotAppendedToTheSungText() {
        val result = parser.parse(
            """
            <tt xmlns:tts="http://www.w3.org/ns/ttml#styling"><body><p begin="00:01.000" end="00:02.000"><span tts:ruby="container"><span tts:ruby="base">所</span><span tts:ruby="textContainer"><span tts:ruby="text">しょ</span></span></span></p></body></tt>
        """.trimIndent(), context
        )
        assertEquals("所", requireNotNull(result.document).lines.single().text.original)
    }

    @Test
    fun structuralMetadataUsesDuetAndNearestStyleBeforeRegionDefaults() {
        val result = parser.parse(
            """
            <tt xmlns="http://www.w3.org/ns/ttml"
                xmlns:itunes="http://music.apple.com/lyric-ttml-internal"
                xmlns:ttm="http://www.w3.org/ns/ttml#metadata"
                xmlns:tts="http://www.w3.org/ns/ttml#styling"
                itunes:timing="Word">
              <head>
                <styling>
                  <style xml:id="parent" tts:textAlign="left" tts:direction="ltr"/>
                  <style xml:id="region-style" tts:textAlign="right" tts:direction="rtl"/>
                </styling>
                <layout><region xml:id="lower" style="region-style"/></layout>
              </head>
              <body><div style="parent">
                <p begin="00:01.000" end="00:02.000" itunes:key="line-1"
                    itunes:duet="duet-a" ttm:agent="lead" region="lower"
                    ttm:role="x-duet"><span begin="00:01.000" end="00:02.000">Lead</span></p>
              </div></body>
            </tt>
        """.trimIndent(), context
        )

        val line = requireNotNull(result.document).lines.single()
        assertEquals("duet-a", line.groupId)
        assertEquals("lead", line.agentId)
        assertEquals("x-duet", line.role)
        assertEquals("lower", line.region)
        assertEquals(LyricsAlignment.LEFT, line.alignment)
        assertEquals(LyricsTextDirection.LTR, line.direction)
    }

    @Test
    fun backgroundTypeAliasIsSeparatedWithoutLeakingIntoMainText() {
        val result = parser.parse(
            """
            <tt xmlns="http://www.w3.org/ns/ttml" xmlns:ttm="http://www.w3.org/ns/ttml#metadata">
              <body><p begin="00:10.000" end="00:12.000" ttm:agent="lead">
                Main<span ttm:type="x-bg backing" ttm:agent="backing">Echo</span>
              </p></body>
            </tt>
        """.trimIndent(), context
        )

        val lines = requireNotNull(result.document).lines
        assertEquals(2, lines.size)
        assertEquals("Main", lines.single { !it.isBackground }.text.original)
        val background = lines.single { it.isBackground }
        assertEquals("Echo", background.text.original)
        assertEquals("backing", background.agentId)
        assertEquals("x-bg backing", background.role)
    }

    @Test
    fun amllWordTimingAggregatesAdjacentSyllablesIntoOneSemanticWord() {
        val result = parser.parse(
            """
            <tt xmlns="http://www.w3.org/ns/ttml"
                xmlns:itunes="http://music.apple.com/lyric-ttml-internal"
                itunes:timing="Word" xml:lang="es">
              <body><p begin="1s" end="1.5s">
                <span begin="1s" end="1.2s">mien</span><span begin="1.2s" end="1.5s">tras</span>
              </p></body>
            </tt>
            """.trimIndent(),
            context,
        )

        val document = requireNotNull(result.document)
        val word = document.lines.single().words.single()
        assertEquals(SyncType.WORD, document.syncType)
        assertTrue(document.hasReliableWordSync)
        assertTrue(LyricsCapability.WORD_SYNC in document.capabilities)
        assertEquals("mientras", word.text)
        assertEquals(1_000L, word.startTimeMs)
        assertEquals(1_500L, word.endTimeMs)
        assertEquals(listOf("mien", "tras"), word.segments.map { it.text })
        assertEquals(listOf(1_000L, 1_200L), word.segments.map { it.startTimeMs })
        assertEquals(listOf(1_200L, 1_500L), word.segments.map { it.endTimeMs })
    }

    @Test
    fun amllReconstructsTwoAndSixTimedSegmentsAsCompleteSemanticWords() {
        val result = parser.parse(
            """
            <tt xmlns="http://www.w3.org/ns/ttml"
                xmlns:itunes="http://music.apple.com/lyric-ttml-internal"
                itunes:timing="Word" xml:lang="es">
              <body>
                <p begin="1s" end="1.4s"><span begin="1s" end="1.2s">cuan</span><span begin="1.2s" end="1.4s">do</span></p>
                <p begin="2s" end="2.4s"><span begin="2s" end="2.2s">mien</span><span begin="2.2s" end="2.4s">tras</span></p>
                <p begin="3s" end="4.2s"><span begin="3s" end="3.2s">ex</span><span begin="3.2s" end="3.4s">tra</span><span begin="3.4s" end="3.6s">or</span><span begin="3.6s" end="3.8s">di</span><span begin="3.8s" end="4s">na</span><span begin="4s" end="4.2s">rio</span></p>
              </body>
            </tt>
            """.trimIndent(),
            context,
        )

        val words = requireNotNull(result.document).lines.map { it.words.single() }
        assertEquals(listOf("cuando", "mientras", "extraordinario"), words.map { it.text })
        assertEquals(listOf(2, 2, 6), words.map { it.segments.size })
        assertEquals(listOf("cuan", "do"), words[0].segments.map { it.text })
        assertEquals(listOf("mien", "tras"), words[1].segments.map { it.text })
        assertEquals(
            listOf("ex", "tra", "or", "di", "na", "rio"),
            words[2].segments.map { it.text },
        )
    }

    @Test
    fun genericTimedSpansUseTextBoundariesInsteadOfTreatingEverySpanAsAWord() {
        val result = parser.parse(
            """
            <tt xmlns="http://www.w3.org/ns/ttml" xml:lang="es">
              <body><p begin="0s" end="0.6s"><span begin="0s" end="0.2s">qui</span><span begin="0.2s" end="0.4s">e</span><span begin="0.4s" end="0.6s">re</span></p></body>
            </tt>
            """.trimIndent(),
            context,
        )

        val word = requireNotNull(result.document).lines.single().words.single()
        assertEquals("quiere", word.text)
        assertEquals(listOf("qui", "e", "re"), word.segments.map { it.text })
        assertEquals(0L, word.startTimeMs)
        assertEquals(600L, word.endTimeMs)
    }

    @Test
    fun oneTimedSpanContainingWhitespaceStillProducesTwoSemanticWords() {
        val result = parser.parse(
            """
            <tt xmlns="http://www.w3.org/ns/ttml" xml:lang="es">
              <body><p begin="0s" end="1s"><span begin="0s" end="1s">te quiero</span></p></body>
            </tt>
            """.trimIndent(),
            context,
        )

        val words = requireNotNull(result.document).lines.single().words
        assertEquals(listOf("te", "quiero"), words.map { it.text })
        assertEquals(listOf(0L, 0L), words.map { it.startTimeMs })
        assertEquals(listOf(1_000L, 1_000L), words.map { it.endTimeMs })
    }

    @Test
    fun oneTimedCjkSpanUsesLinguisticWordSegmentation() {
        val result = parser.parse(
            """
            <tt xmlns="http://www.w3.org/ns/ttml" xml:lang="ja">
              <body><p begin="0s" end="1s"><span begin="0s" end="1s">私は学生</span></p></body>
            </tt>
            """.trimIndent(),
            context,
        )

        val words = requireNotNull(result.document).lines.single().words
        assertEquals("私は学生", words.joinToString(separator = "") { it.text })
        assertTrue(words.size in 2..3)
        assertTrue(words.all { it.startTimeMs == 0L && it.endTimeMs == 1_000L })
    }

    @Test
    fun metadataAgentOrderStylesSecondSingerBeforeTheFirstLyricFrame() {
        val result = parser.parse(
            """
            <tt xmlns="http://www.w3.org/ns/ttml" xmlns:ttm="http://www.w3.org/ns/ttml#metadata">
              <head><metadata>
                <ttm:agent xml:id="v1" type="person"/>
                <ttm:agent xml:id="v2" type="person"/>
              </metadata></head>
              <body>
                <p begin="1s" end="2s" ttm:agent="v2">Second singer starts</p>
                <p begin="2s" end="3s" ttm:agent="v1">Lead follows</p>
              </body>
            </tt>
            """.trimIndent(),
            context,
        )

        val lines = requireNotNull(result.document).lines
        assertEquals(LyricsAlignment.RIGHT, lines[0].alignment)
        assertEquals(LyricsAlignment.LEFT, lines[1].alignment)
    }

    @Test
    fun appleWordTimingDetectsFormattedAmlTtmlAndKeepsSyllablesInsideOneWord() {
        val result = parser.parse(
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <tt xmlns="http://www.w3.org/ns/ttml"
                xmlns:ttm="http://www.w3.org/ns/ttml#metadata"
                xmlns:itunes="http://music.apple.com/lyric-ttml-internal"
                itunes:timing="Word">
              <head><metadata><ttm:agent xml:id="v1" type="person"/></metadata></head>
              <body dur="00:20.000"><div itunes:song-part="Verse">
                <p begin="00:01.000" end="00:04.000" itunes:key="L1" ttm:agent="v1">
                  <span begin="00:01.000" end="00:01.700">Me </span>
                  <span begin="00:01.700" end="00:02.500">tiene </span>
                  <span begin="00:02.500" end="00:03.200">pensando </span>
                  <span begin="00:03.200" end="00:04.000">ayer</span>
                </p>
                <p begin="00:04.200" end="00:06.000" itunes:key="L2" ttm:agent="v1">
                  <span begin="00:04.200" end="00:04.600">cuan</span>
                  <span begin="00:04.600" end="00:05.000">do </span>
                  <span begin="00:05.000" end="00:06.000">volví</span>
                </p>
              </div></body>
            </tt>
            """.trimIndent(),
            context,
        )

        val document = requireNotNull(result.document)
        assertEquals(SyncType.WORD, document.syncType)
        assertTrue(document.hasReliableWordSync)
        assertEquals(
            listOf("Me", "tiene", "pensando", "ayer"),
            document.lines[0].words.map { it.text },
        )
        assertEquals(listOf("cuando", "volví"), document.lines[1].words.map { it.text })
        val whenWord = document.lines[1].words.first()
        assertEquals(4_200L, whenWord.startTimeMs)
        assertEquals(5_000L, whenWord.endTimeMs)
        assertEquals(listOf("cuan", "do "), whenWord.segments.map { it.text })
    }

    @Test
    fun appleWordTimingPreservesSpacesInsideAndBetweenTimedSpans() {
        val result = parser.parse(
            """
            <tt xmlns="http://www.w3.org/ns/ttml"
                xmlns:itunes="http://music.apple.com/lyric-ttml-internal"
                itunes:timing="Word">
              <body><p begin="1s" end="4s"><span begin="1s" end="2s">inside </span><span begin="2s" end="3s">between</span> <span begin="3s" end="4s">spaces</span></p></body>
            </tt>
            """.trimIndent(),
            context,
        )

        val line = requireNotNull(result.document).lines.single()
        assertEquals(SyncType.WORD, result.document?.syncType)
        assertEquals(listOf("inside", "between", "spaces"), line.words.map { it.text })
        assertEquals(listOf("inside ", "between ", "spaces"), line.words.flatMap { it.segments }.map { it.text })
    }

    @Test
    fun appleTimingUsesNamespaceUriInsteadOfPrefixName() {
        val result = parser.parse(
            """
            <tt xmlns="http://www.w3.org/ns/ttml"
                xmlns:apple="http://music.apple.com/lyric-ttml-internal"
                apple:timing="Word">
              <body><p begin="1s" end="2s"><span begin="1s" end="1.4s">cuan</span><span begin="1.4s" end="2s">do</span></p></body>
            </tt>
            """.trimIndent(),
            context,
        )

        val document = requireNotNull(result.document)
        assertEquals(SyncType.WORD, document.syncType)
        assertEquals(listOf("cuando"), document.lines.single().words.map { it.text })
    }

    @Test
    fun appleWordTimingRejectsTimedSpansOutsideTheParagraphRange() {
        val result = parser.parse(
            """
            <tt xmlns="http://www.w3.org/ns/ttml"
                xmlns:itunes="http://music.apple.com/lyric-ttml-internal"
                itunes:timing="Word">
              <body><p begin="1s" end="2s"><span begin="0.5s" end="1.5s">outside</span></p></body>
            </tt>
            """.trimIndent(),
            context,
        )

        val document = requireNotNull(result.document)
        assertEquals(SyncType.LINE, document.syncType)
        assertFalse(document.hasReliableWordSync)
        assertTrue(document.lines.single().words.isEmpty())
    }

    @Test
    fun partOfWordMetadataAggregatesSyllablesAndPreservesSegmentTiming() {
        val result = parser.parse(
            """
            <tt xmlns="http://www.w3.org/ns/ttml" xmlns:amll="urn:amll">
              <body><p begin="0s" end="3s">
                <span begin="0.743s" end="0.981s" amll:IsPartOfWord="true">em</span><span begin="0.981s" end="2.012s" amll:partofword="true">pie</span><span begin="2.012s" end="2.673s" amll:wordPart="false">zas</span>
              </p></body>
            </tt>
            """.trimIndent(),
            context,
        )

        val word = requireNotNull(result.document).lines.single().words.single()
        assertEquals(SyncType.WORD, result.document?.syncType)
        assertTrue(requireNotNull(result.document).hasReliableWordSync)
        assertTrue(LyricsCapability.WORD_SYNC in result.document!!.capabilities)
        assertEquals("empiezas", word.text)
        assertEquals(743L, word.startTimeMs)
        assertEquals(2_673L, word.endTimeMs)
        assertEquals(listOf("em", "pie", "zas"), word.segments.map { it.text })
        assertEquals(listOf(743L, 981L, 2_012L), word.segments.map { it.startTimeMs })
        assertEquals(listOf(981L, 2_012L, 2_673L), word.segments.map { it.endTimeMs })
    }

    @Test
    fun invalidInternalTimingDoesNotAdvertiseWordSync() {
        val result = parser.parse(
            """
            <tt xmlns="http://www.w3.org/ns/ttml"><body><p begin="0s" end="3s">
              <span begin="1s" end="1s">Invalid</span>
            </p></body></tt>
            """.trimIndent(),
            context,
        )

        val document = requireNotNull(result.document)
        assertEquals(SyncType.LINE, document.syncType)
        assertFalse(document.hasReliableWordSync)
        assertFalse(LyricsCapability.WORD_SYNC in document.capabilities)
    }

    @Test
    fun untimedWrapperProvidesStructuralWordBoundaryWithoutTemporalInference() {
        val result = parser.parse(
            """
            <tt xmlns="http://www.w3.org/ns/ttml"><body><p begin="0s" end="2s">
              <span><span begin="0s" end="0.3s">dí</span><span begin="0.3s" end="0.6s">me</span><span begin="0.6s" end="1s">lo</span></span> <span begin="1s" end="1.5s">ya</span> <span begin="1.5s" end="2s">mismo</span>
            </p></body></tt>
            """.trimIndent(),
            context,
        )

        val words = requireNotNull(result.document).lines.single().words
        assertEquals(listOf("dímelo", "ya", "mismo"), words.map { it.text })
        assertEquals(3, words.first().segments.size)
    }

    @Test
    fun punctuationAndConnectorsFollowTextBoundariesInsteadOfSpanBoundaries() {
        val result = parser.parse(
            """
            <tt xmlns="http://www.w3.org/ns/ttml"><body><p begin="0s" end="2s"><span begin="0s" end="0.2s">don</span><span begin="0.2s" end="0.3s">'</span><span begin="0.3s" end="0.5s">t </span><span begin="0.5s" end="0.8s">oh-</span><span begin="0.8s" end="1s">oh </span><span begin="1s" end="1.1s">yeah</span><span begin="1.1s" end="1.2s">! </span><span begin="1.2s" end="1.25s">¿</span><span begin="1.25s" end="1.3s">Dónde</span><span begin="1.3s" end="1.35s">? </span><span begin="1.35s" end="1.4s">fast</span><span begin="1.4s" end="1.45s">next</span></p></body></tt>
            """.trimIndent(),
            context,
        )

        assertEquals(
            listOf("don't", "oh-oh", "yeah!", "¿Dónde?", "fastnext"),
            requireNotNull(result.document).lines.single().words.map { it.text },
        )
    }
}
