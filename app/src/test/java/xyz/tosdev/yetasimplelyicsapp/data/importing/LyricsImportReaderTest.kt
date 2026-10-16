package xyz.tosdev.yetasimplelyicsapp.data.importing

import org.junit.Assert.assertEquals
import org.junit.Test

class LyricsImportReaderTest {
    @Test fun readsUtf8WithBomAndAccents() {
        val text = "<tt>corazón 音楽</tt>"
        val bytes = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + text.toByteArray()
        assertEquals(text, LyricsImportReader.read(bytes.inputStream()))
    }
    @Test fun readsUtf16WithBom() {
        val text = "<tt>canción</tt>"
        assertEquals(text, LyricsImportReader.read(text.toByteArray(Charsets.UTF_16).inputStream()))
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsOversizedInput() {
        LyricsImportReader.read(ByteArray(LyricsImportReader.MAX_BYTES + 1) { 65 }.inputStream())
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsEmptyFiles() {
        LyricsImportReader.read(ByteArray(0).inputStream())
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsInvalidEncoding() {
        LyricsImportReader.read(byteArrayOf(0xC3.toByte(), 0x28).inputStream())
    }
}
