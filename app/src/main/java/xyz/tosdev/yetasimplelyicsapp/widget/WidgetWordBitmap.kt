package xyz.tosdev.yetasimplelyicsapp.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import java.util.Locale
import kotlin.math.ceil

internal data class WidgetWordBitmap(val bitmap: Bitmap, val textLayout: WidgetTextLayout, val renderedLineCount: Int)

/**
 * Glance TextViews inherit launcher/vendor font metrics and can rewrap already fitted fragments.
 * Measure and draw with the very same Paint instead. This is a small transparent glyph raster,
 * refreshed only at semantic word/config/size boundaries, never a frame-rate bitmap animation.
 */
internal fun renderWidgetWord(
    context: Context,
    word: String,
    languageTag: String?,
    availableWidthDp: Float,
    availableHeightDp: Float,
    textScale: Float,
    color: Int,
): WidgetWordBitmap {
    val metrics = context.resources.displayMetrics
    val density = metrics.density.coerceAtLeast(0.5f)
    val scaledDensity = metrics.scaledDensity.coerceAtLeast(0.5f)
    val widthDp = availableWidthDp.coerceAtLeast(1f)
    val heightDp = availableHeightDp.coerceAtLeast(1f)
    // At most 1 MiB ARGB per widget; no oversized RemoteViews parcels on large launchers.
    val rasterScale = minOf(1f, 512f / maxOf(widthDp * density, heightDp * density))
    fun pixels(sizeSp: Float) = android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_SP, sizeSp, metrics) * rasterScale
    val widthPx = (widthDp * density * rasterScale).toInt().coerceAtLeast(1)
    val heightPx = (heightDp * density * rasterScale).toInt().coerceAtLeast(1)
    val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; this.color = color }
    val measurer = object : WidgetTextMeasurer {
        override fun measureWidthDp(text: String, fontSizeSp: Float): Float {
            paint.textSize = pixels(fontSizeSp)
            return paint.measureText(text) / (density * rasterScale)
        }
        override fun measureLineHeightDp(fontSizeSp: Float): Float {
            paint.textSize = pixels(fontSizeSp)
            return ceil((paint.fontMetrics.descent - paint.fontMetrics.ascent).toDouble()).toFloat() /
                (density * rasterScale)
        }
    }
    val layout = WordLineBreaker.layoutWord(word, languageTag,
        context.resources.configuration.locales.get(0) ?: Locale.ROOT,
        // Reserve one raster pixel for rounding/ink overhang, using actual raster constraints.
        (widthPx - 1).coerceAtLeast(1) / (density * rasterScale),
        metrics.scaledDensity / density, 32f * textScale, (18f * textScale).coerceAtLeast(14f),
        currentWordLineCapacity(heightDp, 9f, metrics.scaledDensity / density), measurer,
        availableHeightDp = heightPx / (density * rasterScale))
    paint.textSize = pixels(layout.fontSizeSp)
    val paragraph = StaticLayout.Builder.obtain(layout.text, 0, layout.text.length, paint, widthPx)
        .setAlignment(Layout.Alignment.ALIGN_CENTER)
        .setTextDirection(TextDirectionHeuristics.FIRSTSTRONG_LTR)
        .setIncludePad(false)
        .setBreakStrategy(android.graphics.text.LineBreaker.BREAK_STRATEGY_SIMPLE)
        .setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NONE)
        .build()
    val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
    Canvas(bitmap).apply {
        translate(0f, ((heightPx - paragraph.height) / 2f).coerceAtLeast(0f))
        paragraph.draw(this)
    }
    return WidgetWordBitmap(bitmap, layout, paragraph.lineCount)
}
