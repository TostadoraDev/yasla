package xyz.tosdev.yetasimplelyicsapp.localization

import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import androidx.annotation.RequiresApi
import androidx.core.content.edit
import java.util.Locale

/** Keeps the YASLA interface locale independent from lyric and media metadata. */
object AppLanguageManager {
    const val TAG_SYSTEM = ""
    const val TAG_ENGLISH = "en"
    const val TAG_SPANISH = "es"

    private const val PREFERENCES_NAME = "app_language"
    private const val KEY_LANGUAGE_TAG = "language_tag"
    private const val KEY_PLATFORM_IMPORTED = "platform_imported"

    fun initialize(context: Context) {
        val prefs = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        if (Build.VERSION.SDK_INT >= 33) {
            val manager = context.getSystemService(LocaleManager::class.java)
            if (!prefs.getBoolean(KEY_PLATFORM_IMPORTED, false)) {
                // Hand custom storage to Android exactly once. A missing preference means a
                // genuinely new install, whose product default is English on every device.
                val storedSelection = prefs.takeIf { it.contains(KEY_LANGUAGE_TAG) }
                    ?.getString(KEY_LANGUAGE_TAG, null)
                val initialTag = initialPlatformTag(
                    platformTag = manager.applicationLocales.get(0)?.language,
                    storedTag = storedSelection,
                )
                if (manager.applicationLocales.isEmpty && initialTag.isNotEmpty()) {
                    manager.applicationLocales = LocaleList.forLanguageTags(initialTag)
                }
                prefs.edit {
                    putString(KEY_LANGUAGE_TAG, initialTag)
                    putBoolean(KEY_PLATFORM_IMPORTED, true)
                }
            } else {
                // Settings > App languages owns the selection after the handoff. Mirroring it
                // keeps pre-33 fallback data current without overwriting an external choice.
                prefs.edit { putString(KEY_LANGUAGE_TAG, platformTag(manager)) }
            }
        } else updateLegacyResources(context, storedTag(context))
    }

    fun currentTag(context: Context): String = if (Build.VERSION.SDK_INT >= 33) {
        val prefs = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        tagBeforePlatformHandoff(
            platformTag = platformTag(context.getSystemService(LocaleManager::class.java)),
            storedTag = prefs.takeIf { it.contains(KEY_LANGUAGE_TAG) }
                ?.getString(KEY_LANGUAGE_TAG, null),
            platformImported = prefs.getBoolean(KEY_PLATFORM_IMPORTED, false),
        )
    } else storedTag(context)

    /** Returns true only when the requested interface language changed. */
    fun apply(context: Context, tag: String): Boolean {
        val normalized = normalizeTag(tag)
        val previous = currentTag(context)
        context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit { putString(KEY_LANGUAGE_TAG, normalized) }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.getSystemService(LocaleManager::class.java).applicationLocales =
                LocaleList.forLanguageTags(normalized)
        } else {
            updateLegacyResources(context.applicationContext, normalized)
        }
        return previous != normalized
    }

    fun wrap(base: Context): Context = localizedContext(base)

    fun localizedContext(context: Context): Context {
        val tag = effectiveTag(context, currentTag(context))
        val configuration = Configuration(context.resources.configuration)
        configuration.setLocales(LocaleList.forLanguageTags(tag))
        return context.createConfigurationContext(configuration)
    }

    internal fun normalizeTag(tag: String?): String = when (
        tag.orEmpty().trim().lowercase(Locale.ROOT).substringBefore('-')
    ) {
        TAG_ENGLISH -> TAG_ENGLISH
        TAG_SPANISH -> TAG_SPANISH
        else -> TAG_SYSTEM
    }

    internal fun initialPlatformTag(platformTag: String?, storedTag: String?): String {
        val platformSelection = normalizeTag(platformTag)
        return when {
            platformSelection.isNotEmpty() -> platformSelection
            storedTag != null -> normalizeTag(storedTag)
            else -> TAG_ENGLISH
        }
    }

    /**
     * Resolves the locale while `Application.attachBaseContext` runs before the one-time
     * Android 13+ handoff. Once imported, an empty platform locale intentionally means
     * "System default" and must not be replaced by the product's fresh-install default.
     */
    internal fun tagBeforePlatformHandoff(
        platformTag: String?,
        storedTag: String?,
        platformImported: Boolean,
    ): String {
        val platformSelection = normalizeTag(platformTag)
        return when {
            platformSelection.isNotEmpty() -> platformSelection
            !platformImported -> initialPlatformTag(platformTag = null, storedTag = storedTag)
            else -> TAG_SYSTEM
        }
    }

    internal fun systemFallbackTag(languageTags: List<String>): String =
        languageTags.asSequence()
            .map(::normalizeTag)
            .firstOrNull { it.isNotEmpty() }
            ?: TAG_ENGLISH

    private fun storedTag(context: Context): String = normalizeTag(
        context
            .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .getString(KEY_LANGUAGE_TAG, TAG_ENGLISH),
    )

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun platformTag(manager: LocaleManager): String =
        normalizeTag(manager.applicationLocales.get(0)?.language)

    private fun effectiveTag(context: Context, selectedTag: String): String {
        if (selectedTag.isNotEmpty()) return selectedTag
        val systemLocales = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.getSystemService(LocaleManager::class.java).systemLocales
        } else {
            android.content.res.Resources.getSystem().configuration.locales
        }
        return systemFallbackTag(
            (0 until systemLocales.size()).map { index -> systemLocales[index].toLanguageTag() },
        )
    }

    @Suppress("DEPRECATION")
    private fun updateLegacyResources(context: Context, tag: String) {
        val configuration = Configuration(context.resources.configuration)
        configuration.setLocales(LocaleList.forLanguageTags(effectiveTag(context, tag)))
        context.resources.updateConfiguration(configuration, context.resources.displayMetrics)
    }
}
