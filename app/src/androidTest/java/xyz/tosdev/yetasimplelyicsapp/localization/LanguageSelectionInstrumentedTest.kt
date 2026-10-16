package xyz.tosdev.yetasimplelyicsapp.localization

import android.app.LocaleManager
import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import xyz.tosdev.yetasimplelyicsapp.R

@RunWith(AndroidJUnit4::class)
class LanguageSelectionInstrumentedTest {
    @Test
    fun nativeLocaleHandoffPersistsSelectionsAndLocalizesEverySurface() {
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        val originalPreferences = preferences.all.toMap()
        val localeManager = context.getSystemService(LocaleManager::class.java)
        val originalApplicationLocales = localeManager.applicationLocales.toLanguageTags()

        try {
            preferences.edit().clear().commit()
            setApplicationLocales(instrumentation, localeManager, "")

            val spanishDeviceContext = context.createConfigurationContext(
                Configuration(context.resources.configuration).apply {
                    setLocales(LocaleList.forLanguageTags("es-MX"))
                },
            )
            assertEquals(
                AppLanguageManager.TAG_ENGLISH,
                AppLanguageManager.currentTag(spanishDeviceContext),
            )

            AppLanguageManager.initialize(spanishDeviceContext)
            assertEquals("en", localeManager.applicationLocales.toLanguageTags())
            assertEquals("en", preferences.getString(KEY_LANGUAGE_TAG, null))
            assertEquals(true, preferences.getBoolean(KEY_PLATFORM_IMPORTED, false))
            assertLocalizedResources(context, expectedLanguage = "en")

            AppLanguageManager.apply(context, AppLanguageManager.TAG_SPANISH)
            instrumentation.waitForIdleSync()
            assertEquals("es", AppLanguageManager.currentTag(context))
            assertEquals("es", preferences.getString(KEY_LANGUAGE_TAG, null))
            assertLocalizedResources(context, expectedLanguage = "es")

            AppLanguageManager.initialize(context)
            assertEquals("es", AppLanguageManager.currentTag(context))
            assertEquals("es", preferences.getString(KEY_LANGUAGE_TAG, null))

            AppLanguageManager.apply(context, AppLanguageManager.TAG_ENGLISH)
            instrumentation.waitForIdleSync()
            assertEquals("en", AppLanguageManager.currentTag(context))
            assertLocalizedResources(context, expectedLanguage = "en")

            AppLanguageManager.apply(context, AppLanguageManager.TAG_SYSTEM)
            instrumentation.waitForIdleSync()
            AppLanguageManager.initialize(context)
            assertEquals("", localeManager.applicationLocales.toLanguageTags())
            assertEquals("", AppLanguageManager.currentTag(context))
            assertEquals("", preferences.getString(KEY_LANGUAGE_TAG, null))
            val expectedSystemLanguage = AppLanguageManager.systemFallbackTag(
                localeManager.systemLocales.asLanguageTags(),
            )
            assertEquals(
                expectedSystemLanguage,
                AppLanguageManager.localizedContext(context)
                    .resources.configuration.locales[0].language,
            )
        } finally {
            setApplicationLocales(instrumentation, localeManager, originalApplicationLocales)
            restorePreferences(preferences, originalPreferences)
        }
    }

    private fun assertLocalizedResources(context: Context, expectedLanguage: String) {
        val localized = AppLanguageManager.localizedContext(context)
        assertEquals(expectedLanguage, localized.resources.configuration.locales[0].language)
        if (expectedLanguage == "es") {
            assertEquals("Ajustes", localized.getString(R.string.ui_nav_settings))
            assertEquals("Abre YASLA y reproduce una canción", localized.getString(R.string.lyrics_widget_empty))
            assertEquals("Letra sincronizada", localized.getString(R.string.system_notification_synced_lyrics))
            assertEquals("No hay un selector de archivos disponible.", localized.getString(R.string.system_file_picker_unavailable))
            assertEquals("DENEGADO", localized.getString(R.string.ui_access_denied))
        } else {
            assertEquals("Settings", localized.getString(R.string.ui_nav_settings))
            assertEquals("Open YASLA and play a song", localized.getString(R.string.lyrics_widget_empty))
            assertEquals("Synced lyrics", localized.getString(R.string.system_notification_synced_lyrics))
            assertEquals("No file picker is available.", localized.getString(R.string.system_file_picker_unavailable))
            assertEquals("DENIED", localized.getString(R.string.ui_access_denied))
        }
    }

    private fun setApplicationLocales(
        instrumentation: android.app.Instrumentation,
        localeManager: LocaleManager,
        languageTags: String,
    ) {
        instrumentation.runOnMainSync {
            localeManager.applicationLocales = LocaleList.forLanguageTags(languageTags)
        }
        instrumentation.waitForIdleSync()
    }

    private fun LocaleList.asLanguageTags(): List<String> =
        (0 until size()).map { index -> this[index].toLanguageTag() }

    private fun restorePreferences(
        preferences: SharedPreferences,
        values: Map<String, *>,
    ) {
        val editor = preferences.edit().clear()
        values.forEach { (key, value) ->
            when (value) {
                is String -> editor.putString(key, value)
                is Boolean -> editor.putBoolean(key, value)
                is Int -> editor.putInt(key, value)
                is Long -> editor.putLong(key, value)
                is Float -> editor.putFloat(key, value)
                is Set<*> -> @Suppress("UNCHECKED_CAST")
                editor.putStringSet(key, value as Set<String>)
            }
        }
        check(editor.commit()) { "Could not restore app language preferences" }
    }

    private companion object {
        const val PREFERENCES_NAME = "app_language"
        const val KEY_LANGUAGE_TAG = "language_tag"
        const val KEY_PLATFORM_IMPORTED = "platform_imported"
    }
}
