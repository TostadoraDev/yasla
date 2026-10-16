package xyz.tosdev.yetasimplelyicsapp.domain.lyrics.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.tosdev.yetasimplelyicsapp.domain.lyrics.LyricsCapability
import xyz.tosdev.yetasimplelyicsapp.domain.lyrics.LyricsSource
import xyz.tosdev.yetasimplelyicsapp.domain.lyrics.SyncType

class TtmlLyricsParserTest {
    private val parser = TtmlLyricsParser()
    private val context = LyricsParseContext(
        documentId = "ttml-doc",
        trackKey = "artist|title",
        source = LyricsSource(providerId = "test"),
    )

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
    fun backgroundVocalsExtendLineEndAndPreserveSpacing() {
        val result = parser.parse(
            """
            <tt xmlns="http://www.w3.org/ns/ttml" xmlns:ttm="http://www.w3.org/ns/ttml#metadata">
              <body><div>
                <p begin="00:00:24.000" end="00:00:26.000">
                  <span begin="00:00:24.000" end="00:00:26.000">error</span><span ttm:role="x-bg"><span begin="00:00:27.000" end="00:00:28.500">(Mi error)</span></span>
                </p>
              </div></body>
            </tt>
            """.trimIndent(),
            context,
        )

        assertTrue(result.isSuccess)
        val document = requireNotNull(result.document)
        val line = document.lines.single()
        assertEquals("error (Mi error)", line.text.original)
        assertEquals(24_000L, line.startTimeMs)
        assertEquals(28_500L, line.endTimeMs)
        assertEquals(2, line.words.size)
    }
}
