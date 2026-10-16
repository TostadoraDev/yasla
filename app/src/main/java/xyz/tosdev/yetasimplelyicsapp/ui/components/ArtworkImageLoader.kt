package xyz.tosdev.yetasimplelyicsapp.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import java.io.InputStream

private const val MAX_ARTWORK_EDGE_PX = 1_024
private const val MAX_CACHED_ARTWORK = 6

private val artworkCache = object : LinkedHashMap<String, Bitmap>(MAX_CACHED_ARTWORK, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Bitmap>?): Boolean =
        size > MAX_CACHED_ARTWORK
}

internal fun decodeArtworkBitmap(context: Context, rawUri: String?): Bitmap? {
    if (rawUri.isNullOrBlank()) return null
    synchronized(artworkCache) { artworkCache[rawUri]?.let { return it } }

    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    runCatching { openArtwork(context, rawUri)?.use { BitmapFactory.decodeStream(it, null, bounds) } }
        .getOrNull()
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

    var sampleSize = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / sampleSize > MAX_ARTWORK_EDGE_PX) {
        sampleSize *= 2
    }
    val options = BitmapFactory.Options().apply {
        inSampleSize = sampleSize
        inPreferredConfig = Bitmap.Config.ARGB_8888
    }
    val bitmap = runCatching {
        openArtwork(context, rawUri)?.use { BitmapFactory.decodeStream(it, null, options) }
    }.getOrNull() ?: return null
    synchronized(artworkCache) { artworkCache[rawUri] = bitmap }
    return bitmap
}

private fun openArtwork(context: Context, rawUri: String): InputStream? {
    val uri = Uri.parse(rawUri)
    return when (uri.scheme?.lowercase()) {
        null, "" -> File(rawUri).takeIf(File::isFile)?.inputStream()
        "file" -> uri.path?.let(::File)?.takeIf(File::isFile)?.inputStream()
        else -> context.contentResolver.openInputStream(uri)
    }
}
