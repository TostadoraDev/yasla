package xyz.tosdev.yetasimplelyicsapp.data.importing

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

enum class LyricsImportFailure { TOO_LARGE, EMPTY, ENCODING }
class LyricsImportException(val reason: LyricsImportFailure) : IllegalArgumentException(reason.name)

/** Bounded, strict decoding before XML parsing. Does not trust provider-reported file size. */
object LyricsImportReader {
    const val MAX_BYTES = 4 * 1024 * 1024

    fun read(input: InputStream): String {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = input.read(buffer)
            if (count == -1) break
            if (output.size() + count > MAX_BYTES) throw LyricsImportException(LyricsImportFailure.TOO_LARGE)
            output.write(buffer, 0, count)
        }
        val bytes = output.toByteArray()
        if (bytes.isEmpty()) throw LyricsImportException(LyricsImportFailure.EMPTY)
        fun at(index: Int) = bytes.getOrNull(index)?.toInt()?.and(255)
        val (charset, skip) = when {
            at(0) == 0xEF && at(1) == 0xBB && at(2) == 0xBF -> Charsets.UTF_8 to 3
            at(0) == 0xFF && at(1) == 0xFE -> Charsets.UTF_16LE to 2
            at(0) == 0xFE && at(1) == 0xFF -> Charsets.UTF_16BE to 2
            at(0) == 0x3C && at(1) == 0 -> Charsets.UTF_16LE to 0
            at(0) == 0 && at(1) == 0x3C -> Charsets.UTF_16BE to 0
            else -> Charsets.UTF_8 to 0
        }
        val text = try {
            charset.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes, skip, bytes.size - skip)).toString()
        } catch (_: java.nio.charset.CharacterCodingException) {
            throw LyricsImportException(LyricsImportFailure.ENCODING)
        }
        if (text.isBlank()) throw LyricsImportException(LyricsImportFailure.EMPTY)
        return text
    }
}
