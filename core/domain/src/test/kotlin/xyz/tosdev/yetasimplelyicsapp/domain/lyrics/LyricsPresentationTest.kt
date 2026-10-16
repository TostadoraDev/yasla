package xyz.tosdev.yetasimplelyicsapp.domain.lyrics

import org.junit.Assert.assertEquals
import org.junit.Test

class LyricsPresentationTest {
    @org.junit.Test
    fun cachedCanonicalAgentsKeepTheSecondarySingerOnTheRightEvenWhenItStartsFirst() {
        val resolved = resolveLyricsAlignments(listOf(
            line("secondary", 0, agentId = "v2"),
            line("lead", 1, agentId = "v1"),
        ))
        assertEquals(LyricsAlignment.RIGHT, resolved.getValue("secondary"))
        assertEquals(LyricsAlignment.LEFT, resolved.getValue("lead"))
    }
    @Test
    fun alternatingForegroundVoicesKeepStableSidesFromTheFirstLine() {
        val lines = listOf(
            line("second-first", 0, agentId = "v2", alignment = LyricsAlignment.RIGHT),
            line("lead", 1, agentId = "v1", alignment = LyricsAlignment.LEFT),
            line("second-again", 2, agentId = "v2"),
        )

        val resolved = resolveLyricsAlignments(lines)

        assertEquals(LyricsAlignment.RIGHT, resolved.getValue("second-first"))
        assertEquals(LyricsAlignment.LEFT, resolved.getValue("lead"))
        assertEquals(LyricsAlignment.RIGHT, resolved.getValue("second-again"))
    }

    @Test
    fun backgroundVoiceInheritsItsForegroundGroupInsteadOfBecomingAnotherDuetSide() {
        val lines = listOf(
            line("background", 0, agentId = "backing", groupId = "g1", isBackground = true),
            line("foreground", 1, agentId = "v2", groupId = "g1", alignment = LyricsAlignment.RIGHT),
        )

        val resolved = resolveLyricsAlignments(lines)

        assertEquals(LyricsAlignment.RIGHT, resolved.getValue("foreground"))
        assertEquals(LyricsAlignment.RIGHT, resolved.getValue("background"))
    }

    @Test
    fun missingAgentMetadataUsesNormalLeftStyle() {
        val resolved = resolveLyricsAlignments(listOf(line("plain", 0)))

        assertEquals(LyricsAlignment.LEFT, resolved.getValue("plain"))
    }

    private fun line(
        id: String,
        order: Int,
        agentId: String? = null,
        groupId: String? = null,
        alignment: LyricsAlignment = LyricsAlignment.AUTO,
        isBackground: Boolean = false,
    ) = LyricsLine(
        id = id,
        order = order,
        text = LocalizedText(id),
        startTimeMs = order * 1_000L,
        endTimeMs = (order + 1) * 1_000L,
        agentId = agentId,
        groupId = groupId,
        alignment = alignment,
        isBackground = isBackground,
    )
}
