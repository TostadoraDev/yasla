package xyz.tosdev.yetasimplelyicsapp.localization

import org.junit.Assert.assertEquals
import org.junit.Test
import xyz.tosdev.yetasimplelyicsapp.ui.SettingsUiState

class LanguageSelectionTest {
    @Test
    fun onlySupportedInterfaceLocalesArePersisted() {
        for (tag in listOf("es", "es-MX", " ES-es ")) assertEquals("es", AppLanguageManager.normalizeTag(tag))
        for (tag in listOf("en", "en-US", " EN-gb ")) assertEquals("en", AppLanguageManager.normalizeTag(tag))
        for (tag in listOf(null, "", "system", "fr", "und")) assertEquals("", AppLanguageManager.normalizeTag(tag))
    }

    @Test
    fun freshInstallStartsInEnglishRegardlessOfSystemLocale() {
        assertEquals("en", AppLanguageManager.initialPlatformTag(platformTag = null, storedTag = null))
        assertEquals("en", SettingsUiState().languageTag)
    }

    @Test
    fun firstPlatformHandoffPreservesExistingSelections() {
        assertEquals("es", AppLanguageManager.initialPlatformTag(platformTag = "es-MX", storedTag = "en"))
        assertEquals("en", AppLanguageManager.initialPlatformTag(platformTag = null, storedTag = "en-US"))
        assertEquals("", AppLanguageManager.initialPlatformTag(platformTag = null, storedTag = ""))
    }

    @Test
    fun applicationBaseContextUsesEnglishBeforeFreshInstallHandoff() {
        assertEquals(
            "en",
            AppLanguageManager.tagBeforePlatformHandoff(
                platformTag = null,
                storedTag = null,
                platformImported = false,
            ),
        )
        assertEquals(
            "es",
            AppLanguageManager.tagBeforePlatformHandoff(
                platformTag = null,
                storedTag = "es-MX",
                platformImported = false,
            ),
        )
    }

    @Test
    fun nativePlatformSelectionRemainsSourceOfTruthAfterHandoff() {
        assertEquals(
            "es",
            AppLanguageManager.tagBeforePlatformHandoff(
                platformTag = "es-MX",
                storedTag = "en",
                platformImported = true,
            ),
        )
        assertEquals(
            "",
            AppLanguageManager.tagBeforePlatformHandoff(
                platformTag = null,
                storedTag = "en",
                platformImported = true,
            ),
        )
    }

    @Test
    fun systemDefaultUsesSupportedLocaleAndFallsBackToEnglish() {
        assertEquals("es", AppLanguageManager.systemFallbackTag(listOf("fr-FR", "es-MX")))
        assertEquals("en", AppLanguageManager.systemFallbackTag(listOf("de-DE", "ja-JP")))
        assertEquals("en", AppLanguageManager.systemFallbackTag(emptyList()))
    }
}
