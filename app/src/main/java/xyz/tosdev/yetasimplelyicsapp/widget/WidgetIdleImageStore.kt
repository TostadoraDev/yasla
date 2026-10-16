package xyz.tosdev.yetasimplelyicsapp.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

internal const val MAX_WIDGET_IMAGE_EDGE_PX = 512

internal class WidgetIdleImageStore(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val imageDirectory = File(appContext.noBackupFilesDir, "widget_idle_images")

    suspend fun importFromPicker(uri: Uri, widgetKey: String): String? = withContext(Dispatchers.IO) {
        val resolver = appContext.contentResolver
        val decoded = if (Build.VERSION.SDK_INT >= 28) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, uri)) { decoder, info, _ ->
                val factor = minOf(1f, MAX_WIDGET_IMAGE_EDGE_PX.toFloat() / maxOf(info.size.width, info.size.height))
                decoder.setTargetSize((info.size.width * factor).toInt().coerceAtLeast(1),
                    (info.size.height * factor).toInt().coerceAtLeast(1))
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } else {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            // decodeStream returns null in bounds-only mode. The stream, not that return value,
            // determines availability; the previous Elvis check rejected every valid picture.
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null
            val options = BitmapFactory.Options().apply {
                inSampleSize = widgetImageSampleSize(bounds.outWidth, bounds.outHeight, MAX_WIDGET_IMAGE_EDGE_PX)
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val bitmap = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
                ?: return@withContext null
            val orientation = runCatching { resolver.openInputStream(uri)?.use {
                ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            } }.getOrNull()
            val matrix = Matrix().apply {
                when (orientation) {
                    ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> setScale(-1f, 1f)
                    ExifInterface.ORIENTATION_ROTATE_180 -> setRotate(180f)
                    ExifInterface.ORIENTATION_FLIP_VERTICAL -> setScale(1f, -1f)
                    ExifInterface.ORIENTATION_TRANSPOSE -> { setRotate(90f); postScale(-1f, 1f) }
                    ExifInterface.ORIENTATION_ROTATE_90 -> setRotate(90f)
                    ExifInterface.ORIENTATION_TRANSVERSE -> { setRotate(-90f); postScale(-1f, 1f) }
                    ExifInterface.ORIENTATION_ROTATE_270 -> setRotate(-90f)
                }
            }
            if (matrix.isIdentity) bitmap else Bitmap.createBitmap(bitmap, 0, 0, bitmap.width,
                bitmap.height, matrix, true).also { if (it !== bitmap) bitmap.recycle() }
        }
        val scaled = decoded.scaleDown(MAX_WIDGET_IMAGE_EDGE_PX)
        if (scaled !== decoded) decoded.recycle()

        imageDirectory.mkdirs()
        val safeKey = widgetKey.replace(Regex("[^A-Za-z0-9_-]"), "_").take(48)
        val file = File(imageDirectory, "${safeKey}_${System.currentTimeMillis()}.img")
        val format = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Bitmap.CompressFormat.WEBP_LOSSY
        } else {
            @Suppress("DEPRECATION")
            Bitmap.CompressFormat.WEBP
        }
        val written = runCatching {
            file.outputStream().buffered().use { output -> scaled.compress(format, 86, output) }
        }.getOrDefault(false)
        scaled.recycle()
        if (written) file.absolutePath else null.also { file.delete() }
    }

    fun delete(path: String?) {
        val target = path?.let(::File) ?: return
        val allowedRoot = runCatching { imageDirectory.canonicalFile }.getOrNull() ?: return
        val candidate = runCatching { target.canonicalFile }.getOrNull() ?: return
        if (candidate.parentFile == allowedRoot) candidate.delete()
    }

    /** Only application-owned, bounded images can enter a RemoteViews parcel. */
    fun load(path: String?): Bitmap? {
        val file = path?.let(::File) ?: return null
        if (runCatching { file.canonicalFile.parentFile != imageDirectory.canonicalFile }.getOrDefault(true)) return null
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, options)
        if (options.outWidth !in 1..MAX_WIDGET_IMAGE_EDGE_PX || options.outHeight !in 1..MAX_WIDGET_IMAGE_EDGE_PX) return null
        return BitmapFactory.decodeFile(file.path)
    }
}

internal fun widgetImageSampleSize(width: Int, height: Int, maxEdgePx: Int): Int {
    if (width <= 0 || height <= 0 || maxEdgePx <= 0) return 1
    var sample = 1
    while (width / (sample * 2) >= maxEdgePx || height / (sample * 2) >= maxEdgePx) {
        sample *= 2
    }
    return sample
}

private fun Bitmap.scaleDown(maxEdgePx: Int): Bitmap {
    val largest = maxOf(width, height)
    if (largest <= maxEdgePx) return this
    val scale = maxEdgePx.toFloat() / largest
    return Bitmap.createScaledBitmap(
        this,
        (width * scale).toInt().coerceAtLeast(1),
        (height * scale).toInt().coerceAtLeast(1),
        true,
    )
}
