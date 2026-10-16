package xyz.tosdev.yetasimplelyicsapp.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class LyricsTextScalePolicyTest {
    @Test
    fun defaultAndPersistedValuesStayInsideTheSupportedRange() {
        assertEquals(1f, SettingsUiState().lyricsTextScale, 0f)
        assertEquals(0.75f, normalizeLyricsTextScale(0.1f), 0f)
        assertEquals(1.25f, normalizeLyricsTextScale(1.25f), 0f)
        assertEquals(1.5f, normalizeLyricsTextScale(4f), 0f)
        assertEquals(1f, normalizeLyricsTextScale(Float.NaN), 0f)
    }

    @Test
    fun primarySecondaryAndTranslationSizesKeepTheirRelativeHierarchy() {
        val scales = listOf(0.75f, 1f, 1.25f, 1.5f)
        scales.forEach { scale ->
            val primary = scaledLyricsSp(36f, scale)
            val background = scaledLyricsSp(21f, scale)
            val translation = scaledLyricsSp(14f, scale)

            assertEquals(36f / 21f, primary / background, 0.0001f)
            assertEquals(21f / 14f, background / translation, 0.0001f)
        }
    }
}
