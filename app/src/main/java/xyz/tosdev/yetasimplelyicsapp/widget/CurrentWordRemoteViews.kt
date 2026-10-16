package xyz.tosdev.yetasimplelyicsapp.widget

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.SizeF
import android.widget.RemoteViews
import androidx.compose.ui.unit.DpSize
import java.util.LinkedHashMap
import java.util.Locale
import xyz.tosdev.yetasimplelyicsapp.MainActivity
import xyz.tosdev.yetasimplelyicsapp.R

/** The same transparent glyph renderer as Glance, without a composition session per word. */
internal fun currentWordRemoteViews(
    context: Context,
    config: LyricsWidgetConfig,
    snapshot: LyricsWidgetSnapshot,
    sizes: List<DpSize>,
): RemoteViews? {
    val validSizes = currentWordSizes(config, snapshot, sizes) ?: return null
    val locale = snapshot.lyricsLanguageTag.takeIf(String::isNotBlank)?.let(Locale::forLanguageTag)
        ?: context.resources.configuration.locales[0]
    val color = runCatching { config.primaryColorHex?.let(android.graphics.Color::parseColor) }
        .getOrNull() ?: context.getColor(R.color.widget_primary_text)
    val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    fun views(size: DpSize): RemoteViews {
        val glyphs = WidgetWordGlyphCache.getOrRender(
            context = context,
            word = snapshot.activeWord,
            languageTag = snapshot.lyricsLanguageTag,
            availableWidthDp = (size.width.value - 8f).coerceAtLeast(1f),
            availableHeightDp = (size.height.value - 8f).coerceAtLeast(1f),
            textScale = config.textScale,
            color = color,
        )
        return RemoteViews(context.packageName, R.layout.widget_current_word_live).apply {
            setImageViewBitmap(R.id.widget_word_glyphs, glyphs.bitmap)
            setContentDescription(R.id.widget_word_glyphs, snapshot.activeWord.uppercase(locale))
            setOnClickPendingIntent(R.id.widget_word_root, open)
        }
    }
    return when {
        validSizes.size == 1 -> views(validSizes.single())
        Build.VERSION.SDK_INT >= 31 -> RemoteViews(validSizes.associate { size ->
            SizeF(size.width.value, size.height.value) to views(size)
        })
        else -> RemoteViews(views(validSizes.first()), views(validSizes.last()))
    }
}

/** Prepares only the next semantic word. It never schedules or displays it early. */
internal fun prewarmNextCurrentWord(
    context: Context,
    config: LyricsWidgetConfig,
    snapshot: LyricsWidgetSnapshot,
    sizes: List<DpSize>,
) {
    if (snapshot.nextWordId.isBlank() || snapshot.nextWord.isBlank()) return
    val next = snapshot.copy(
        activeWordId = snapshot.nextWordId,
        activeWord = snapshot.nextWord,
    )
    val validSizes = currentWordSizes(config, next, sizes) ?: return
    val color = runCatching { config.primaryColorHex?.let(android.graphics.Color::parseColor) }
        .getOrNull() ?: context.getColor(R.color.widget_primary_text)
    validSizes.forEach { size ->
        WidgetWordGlyphCache.getOrRender(
            context = context,
            word = next.activeWord,
            languageTag = next.lyricsLanguageTag,
            availableWidthDp = (size.width.value - 8f).coerceAtLeast(1f),
            availableHeightDp = (size.height.value - 8f).coerceAtLeast(1f),
            textScale = config.textScale,
            color = color,
        )
    }
}

private fun currentWordSizes(
    config: LyricsWidgetConfig,
    snapshot: LyricsWidgetSnapshot,
    sizes: List<DpSize>,
): List<DpSize>? {
    if (snapshot.resolvedPresentationState != WidgetPresentationState.LYRICS_AVAILABLE ||
        !snapshot.hasTrack || snapshot.activeWord.isBlank()
    ) return null
    val validSizes = sizes.filter { it.width.value > 0 && it.height.value > 0 }.distinct()
    if (validSizes.isEmpty() || validSizes.any { size ->
            val policy = config.renderPolicy(size.width.value, size.height.value)
            config.resolveContent(snapshot, policy.sizeClass) != ResolvedWidgetContent.CURRENT_WORD
        }
    ) return null
    return validSizes
}

private data class WidgetWordGlyphKey(
    val word: String,
    val languageTag: String,
    val fallbackLocaleTag: String,
    val widthBits: Int,
    val heightBits: Int,
    val textScaleBits: Int,
    val color: Int,
    val densityDpi: Int,
    val fontScaleBits: Int,
)

/** A small process cache shared by live delivery and the one-word lookahead worker. */
private object WidgetWordGlyphCache {
    private const val MAX_RETAINED_BYTES = 4 * 1024 * 1024
    private val entries = LinkedHashMap<WidgetWordGlyphKey, WidgetWordBitmap>(16, 0.75f, true)
    private var retainedBytes = 0

    @Synchronized
    fun getOrRender(
        context: Context,
        word: String,
        languageTag: String?,
        availableWidthDp: Float,
        availableHeightDp: Float,
        textScale: Float,
        color: Int,
    ): WidgetWordBitmap {
        val configuration = context.resources.configuration
        val metrics = context.resources.displayMetrics
        val key = WidgetWordGlyphKey(
            word = word,
            languageTag = languageTag.orEmpty(),
            fallbackLocaleTag = configuration.locales[0]?.toLanguageTag().orEmpty(),
            widthBits = availableWidthDp.toBits(),
            heightBits = availableHeightDp.toBits(),
            textScaleBits = textScale.toBits(),
            color = color,
            densityDpi = metrics.densityDpi,
            fontScaleBits = configuration.fontScale.toBits(),
        )
        entries[key]?.let { return it }
        val rendered = renderWidgetWord(
            context,
            word,
            languageTag,
            availableWidthDp,
            availableHeightDp,
            textScale,
            color,
        )
        entries[key] = rendered
        retainedBytes += rendered.bitmap.allocationByteCount
        while (retainedBytes > MAX_RETAINED_BYTES && entries.size > 1) {
            val oldest = entries.entries.iterator()
            retainedBytes -= oldest.next().value.bitmap.allocationByteCount
            oldest.remove()
        }
        return rendered
    }
}
