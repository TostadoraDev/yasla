package xyz.tosdev.yetasimplelyicsapp.data.preferences

import android.content.Context
import xyz.tosdev.yetasimplelyicsapp.ui.KaraokeVisualStyle
import xyz.tosdev.yetasimplelyicsapp.ui.SettingsUiState
import xyz.tosdev.yetasimplelyicsapp.ui.normalizeLyricsTextScale

class VisualPreferences(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    fun load(): SettingsUiState {
        val defaults = SettingsUiState()
        val defaultStyle = defaults.karaokeStyle
        return SettingsUiState(
            themeMode = enumValue(preferences.getString(KEY_THEME, null), defaults.themeMode),
            dynamicColor = preferences.getBoolean(KEY_DYNAMIC_COLOR, defaults.dynamicColor),
            motionLevel = enumValue(preferences.getString(KEY_MOTION, null), defaults.motionLevel),
            translationLanguage = preferences.getString(KEY_TRANSLATION_LANGUAGE, null)
                ?.trim()
                ?.takeIf(String::isNotEmpty)
                ?: defaults.translationLanguage,
            preferWordSync = preferences.getBoolean(KEY_WORD_SYNC, defaults.preferWordSync),
            lyricsAlignment = preferences.getString(KEY_ALIGNMENT, null)
                ?.takeIf { it in VALID_ALIGNMENTS }
                ?: defaults.lyricsAlignment,
            lyricsDisplayMode = enumValue(
                preferences.getString(KEY_LYRICS_DISPLAY_MODE, null),
                defaults.lyricsDisplayMode,
            ),
            lyricsTextScale = normalizeLyricsTextScale(
                preferences.getFloat(KEY_LYRICS_TEXT_SCALE, defaults.lyricsTextScale),
            ),
            currentWordAlignment = enumValue(
                preferences.getString(KEY_CURRENT_WORD_ALIGNMENT, null),
                defaults.currentWordAlignment,
            ),
            currentWordFallbackToLine = preferences.getBoolean(
                KEY_CURRENT_WORD_FALLBACK,
                defaults.currentWordFallbackToLine,
            ),
            keepScreenAwake = preferences.getBoolean(
                KEY_KEEP_SCREEN_AWAKE,
                defaults.keepScreenAwake
            ),
            highContrastLyrics = preferences.getBoolean(
                KEY_HIGH_CONTRAST,
                defaults.highContrastLyrics
            ),
            karaokeStyle = KaraokeVisualStyle(
                highlight = enumValue(
                    preferences.getString(KEY_HIGHLIGHT_STYLE, null),
                    defaultStyle.highlight,
                ),
                inactive = enumValue(
                    preferences.getString(KEY_INACTIVE_STYLE, null),
                    defaultStyle.inactive,
                ),
                customStartArgb = preferences.getLong(
                    KEY_CUSTOM_START,
                    defaultStyle.customStartArgb
                )
                    .normalizeArgb(defaultStyle.customStartArgb),
                customEndArgb = preferences.getLong(KEY_CUSTOM_END, defaultStyle.customEndArgb)
                    .normalizeArgb(defaultStyle.customEndArgb),
                inactiveArgb = preferences.getLong(KEY_INACTIVE_COLOR, defaultStyle.inactiveArgb)
                    .normalizeArgb(defaultStyle.inactiveArgb),
                featherDp = preferences.getFloat(KEY_FEATHER, defaultStyle.featherDp)
                    .coerceIn(MIN_FEATHER_DP, MAX_FEATHER_DP),
                inactiveOpacity = preferences.getFloat(
                    KEY_INACTIVE_OPACITY,
                    defaultStyle.inactiveOpacity
                )
                    .coerceIn(MIN_INACTIVE_OPACITY, MAX_INACTIVE_OPACITY),
            ),
        )
    }

    fun save(state: SettingsUiState) {
        val normalized = state.normalized()
        preferences.edit()
            .putString(KEY_THEME, normalized.themeMode.name)
            .putBoolean(KEY_DYNAMIC_COLOR, normalized.dynamicColor)
            .putString(KEY_MOTION, normalized.motionLevel.name)
            .putString(KEY_TRANSLATION_LANGUAGE, normalized.translationLanguage)
            .putBoolean(KEY_WORD_SYNC, normalized.preferWordSync)
            .putString(KEY_ALIGNMENT, normalized.lyricsAlignment)
            .putString(KEY_LYRICS_DISPLAY_MODE, normalized.lyricsDisplayMode.name)
            .putFloat(KEY_LYRICS_TEXT_SCALE, normalized.lyricsTextScale)
            .putString(KEY_CURRENT_WORD_ALIGNMENT, normalized.currentWordAlignment.name)
            .putBoolean(KEY_CURRENT_WORD_FALLBACK, normalized.currentWordFallbackToLine)
            .putBoolean(KEY_KEEP_SCREEN_AWAKE, normalized.keepScreenAwake)
            .putBoolean(KEY_HIGH_CONTRAST, normalized.highContrastLyrics)
            .putString(KEY_HIGHLIGHT_STYLE, normalized.karaokeStyle.highlight.name)
            .putString(KEY_INACTIVE_STYLE, normalized.karaokeStyle.inactive.name)
            .putLong(KEY_CUSTOM_START, normalized.karaokeStyle.customStartArgb)
            .putLong(KEY_CUSTOM_END, normalized.karaokeStyle.customEndArgb)
            .putLong(KEY_INACTIVE_COLOR, normalized.karaokeStyle.inactiveArgb)
            .putFloat(KEY_FEATHER, normalized.karaokeStyle.featherDp)
            .putFloat(KEY_INACTIVE_OPACITY, normalized.karaokeStyle.inactiveOpacity)
            .apply()
    }

    private fun SettingsUiState.normalized(): SettingsUiState = copy(
        translationLanguage = translationLanguage.trim().takeIf(String::isNotEmpty) ?: "Sistema",
        lyricsAlignment = lyricsAlignment.takeIf { it in VALID_ALIGNMENTS } ?: "AUTO",
        lyricsTextScale = normalizeLyricsTextScale(lyricsTextScale),
        karaokeStyle = karaokeStyle.copy(
            customStartArgb = karaokeStyle.customStartArgb.normalizeArgb(0xFFFFFFFF),
            customEndArgb = karaokeStyle.customEndArgb.normalizeArgb(0xFFA8C7FA),
            inactiveArgb = karaokeStyle.inactiveArgb.normalizeArgb(0xFF8D9096),
            featherDp = karaokeStyle.featherDp.coerceIn(MIN_FEATHER_DP, MAX_FEATHER_DP),
            inactiveOpacity = karaokeStyle.inactiveOpacity.coerceIn(
                MIN_INACTIVE_OPACITY,
                MAX_INACTIVE_OPACITY,
            ),
        ),
    )

    private fun Long.normalizeArgb(fallback: Long): Long =
        takeIf { it in 0L..0xFFFFFFFFL } ?: fallback

    private inline fun <reified T : Enum<T>> enumValue(value: String?, fallback: T): T =
        value?.let { runCatching { enumValueOf<T>(it) }.getOrNull() } ?: fallback

    private companion object {
        const val PREFERENCES_NAME = "visual_preferences"
        const val KEY_THEME = "theme"
        const val KEY_DYNAMIC_COLOR = "dynamic_color"
        const val KEY_MOTION = "motion"
        const val KEY_TRANSLATION_LANGUAGE = "translation_language"
        const val KEY_WORD_SYNC = "prefer_word_sync"
        const val KEY_ALIGNMENT = "lyrics_alignment"
        const val KEY_LYRICS_DISPLAY_MODE = "lyrics_display_mode"
        const val KEY_LYRICS_TEXT_SCALE = "lyrics_text_scale"
        const val KEY_CURRENT_WORD_ALIGNMENT = "current_word_alignment"
        const val KEY_CURRENT_WORD_FALLBACK = "current_word_fallback_to_line"
        const val KEY_KEEP_SCREEN_AWAKE = "keep_screen_awake"
        const val KEY_HIGH_CONTRAST = "high_contrast_lyrics"
        const val KEY_HIGHLIGHT_STYLE = "highlight_style"
        const val KEY_INACTIVE_STYLE = "inactive_style"
        const val KEY_CUSTOM_START = "custom_start_argb"
        const val KEY_CUSTOM_END = "custom_end_argb"
        const val KEY_INACTIVE_COLOR = "inactive_argb"
        const val KEY_FEATHER = "feather_dp"
        const val KEY_INACTIVE_OPACITY = "inactive_opacity"
        const val MIN_FEATHER_DP = 0f
        const val MAX_FEATHER_DP = 32f
        const val MIN_INACTIVE_OPACITY = 0.1f
        const val MAX_INACTIVE_OPACITY = 1f
        val VALID_ALIGNMENTS = setOf("AUTO", "START", "CENTER", "END")
    }
}
