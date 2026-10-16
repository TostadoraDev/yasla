package xyz.tosdev.yetasimplelyicsapp.data.artwork

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ArtworkCacheInstrumentedTest {
    @Test
    fun bitmapFallbackIsStoredAsBoundedStableFileUri() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.cacheDir, "artwork-test-${UUID.randomUUID()}")
        val source = Bitmap.createBitmap(1_600, 1_000, Bitmap.Config.ARGB_8888).apply {
            eraseColor(0xFF315A78.toInt())
        }

        try {
            val cache = ArtworkCache(context, directory = directory)
            val first = cache.cache("track-key", sourceUri = null, sourceBitmap = source)
            val second = cache.cache("track-key", sourceUri = first, sourceBitmap = null)

            assertNotNull(first)
            assertEquals(first, second)
            val file = File(requireNotNull(Uri.parse(first).path))
            assertTrue(file.isFile)
            assertTrue(file.length() in 1..1_500_000)
            val bounds = BitmapFactory.Options().also { it.inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            assertTrue(maxOf(bounds.outWidth, bounds.outHeight) <= 768)
        } finally {
            source.recycle()
            directory.deleteRecursively()
        }
    }
}
