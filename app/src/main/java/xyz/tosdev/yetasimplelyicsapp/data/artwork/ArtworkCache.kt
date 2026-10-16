package xyz.tosdev.yetasimplelyicsapp.data.artwork

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.AtomicFile
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.net.URL
import java.security.MessageDigest
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Copies MediaSession artwork into app-owned storage so library covers remain available
 * after the source player revokes a content URI or discards its MediaMetadata bitmap.
 */
class ArtworkCache(
    context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val directory: File = File(context.filesDir, DIRECTORY_NAME),
) {
    private val resolver: ContentResolver = context.applicationContext.contentResolver

    suspend fun cache(
        trackKey: String,
        sourceUri: String?,
        sourceBitmap: Bitmap?,
    ): String? = withContext(ioDispatcher) {
        if (trackKey.isBlank()) return@withContext null
        val target = File(directory, filename(trackKey))
        if (sourceUri?.let { pointsTo(it, target) } == true && target.isFile) {
            return@withContext Uri.fromFile(target).toString()
        }

        val decodedFromUri = sourceUri
            ?.let(::open)
            ?.use(::decodeBounded)
        val bitmap = decodedFromUri ?: sourceBitmap ?: return@withContext null
        val encoded = try {
            encodeBounded(bitmap)
        } finally {
            if (decodedFromUri != null && decodedFromUri !== sourceBitmap) {
                decodedFromUri.recycle()
            }
        } ?: return@withContext null

        directory.mkdirs()
        val atomic = AtomicFile(target)
        val output = atomic.startWrite()
        try {
            output.write(encoded)
            atomic.finishWrite(output)
        } catch (error: Exception) {
            atomic.failWrite(output)
            throw error
        }
        Uri.fromFile(target).toString()
    }

    private fun open(value: String): InputStream? = runCatching {
        val uri = Uri.parse(value)
        when (uri.scheme?.lowercase()) {
            ContentResolver.SCHEME_CONTENT,
            ContentResolver.SCHEME_ANDROID_RESOURCE,
            -> resolver.openInputStream(uri)
            ContentResolver.SCHEME_FILE -> FileInputStream(requireNotNull(uri.path))
            "http", "https" -> URL(value).openConnection().apply {
                connectTimeout = NETWORK_TIMEOUT_MS
                readTimeout = NETWORK_TIMEOUT_MS
                useCaches = true
            }.getInputStream()
            null -> FileInputStream(value)
            else -> null
        }
    }.getOrNull()

    private fun decodeBounded(input: InputStream): Bitmap? {
        val bytes = readBounded(input) ?: return null
        val bounds = BitmapFactory.Options().also { it.inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sampleSize = 1
        while (max(bounds.outWidth, bounds.outHeight) / sampleSize > DECODE_MAX_DIMENSION) {
            sampleSize *= 2
        }
        return BitmapFactory.decodeByteArray(
            bytes,
            0,
            bytes.size,
            BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            },
        )
    }

    private fun readBounded(input: InputStream): ByteArray? {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(BUFFER_SIZE)
        var total = 0
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            total += count
            if (total > MAX_SOURCE_BYTES) return null
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    private fun encodeBounded(source: Bitmap): ByteArray? {
        var current = scaleDown(source, MAX_DIMENSION)
        val dimensions = intArrayOf(MAX_DIMENSION, 640, 512, 384)
        val qualities = intArrayOf(86, 78, 70, 62)
        try {
            for (index in dimensions.indices) {
                val resized = scaleDown(current, dimensions[index])
                if (resized !== current) {
                    if (current !== source) current.recycle()
                    current = resized
                }
                val output = ByteArrayOutputStream()
                if (current.compress(Bitmap.CompressFormat.WEBP, qualities[index], output)) {
                    val bytes = output.toByteArray()
                    if (bytes.size <= MAX_STORED_BYTES) return bytes
                }
            }
            return null
        } finally {
            if (current !== source) current.recycle()
        }
    }

    private fun scaleDown(source: Bitmap, maximumDimension: Int): Bitmap {
        val largest = max(source.width, source.height)
        if (largest <= maximumDimension) return source
        val scale = maximumDimension.toFloat() / largest
        val width = (source.width * scale).roundToInt().coerceAtLeast(1)
        val height = (source.height * scale).roundToInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(source, width, height, true)
    }

    private fun pointsTo(uriValue: String, file: File): Boolean =
        runCatching {
            val uri = Uri.parse(uriValue)
            uri.scheme == ContentResolver.SCHEME_FILE &&
                File(requireNotNull(uri.path)).canonicalFile == file.canonicalFile
        }.getOrDefault(false)

    private fun filename(trackKey: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(trackKey.toByteArray(Charsets.UTF_8))
        return digest.take(16).joinToString("") { byte -> "%02x".format(byte) } + ".webp"
    }

    companion object {
        private const val DIRECTORY_NAME = "artwork"
        private const val BUFFER_SIZE = 8 * 1024
        private const val MAX_SOURCE_BYTES = 8 * 1024 * 1024
        private const val MAX_STORED_BYTES = 1_500_000
        private const val DECODE_MAX_DIMENSION = 1_536
        private const val MAX_DIMENSION = 768
        private const val NETWORK_TIMEOUT_MS = 8_000
    }
}
