package xyz.tosdev.yetasimplelyicsapp.data.provider

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import xyz.tosdev.yetasimplelyicsapp.domain.lyrics.LyricsCapability
import xyz.tosdev.yetasimplelyicsapp.domain.lyrics.LyricsDocument
import xyz.tosdev.yetasimplelyicsapp.domain.lyrics.LyricsProvider
import xyz.tosdev.yetasimplelyicsapp.domain.lyrics.LyricsProviderDescriptor
import xyz.tosdev.yetasimplelyicsapp.domain.lyrics.LyricsProviderResult
import xyz.tosdev.yetasimplelyicsapp.domain.lyrics.LyricsRequest
import xyz.tosdev.yetasimplelyicsapp.domain.lyrics.LyricsSource
import xyz.tosdev.yetasimplelyicsapp.domain.lyrics.parser.LrcLyricsParser
import xyz.tosdev.yetasimplelyicsapp.domain.lyrics.parser.LyricsParseContext

class LrclibLyricsProvider(
    private val endpoint: String = DEFAULT_ENDPOINT,
    private val connectionFactory: (URL) -> HttpURLConnection = {
        it.openConnection() as HttpURLConnection
    },
) : LyricsProvider {
    override val descriptor = LyricsProviderDescriptor(
        id = "lrclib",
        displayName = "LRCLIB",
        capabilities = setOf(
            LyricsCapability.PLAIN_TEXT,
            LyricsCapability.LINE_SYNC,
        ),
        requiresNetwork = true,
    )

    override suspend fun findLyrics(request: LyricsRequest): LyricsProviderResult =
        withContext(Dispatchers.IO) {
            val connection = connectionFactory(URL(buildUrl(request)))
            try {
                connection.requestMethod = "GET"
                connection.connectTimeout = CONNECT_TIMEOUT_MS
                connection.readTimeout = READ_TIMEOUT_MS
                connection.setRequestProperty("Accept", "application/json")
                connection.setRequestProperty("User-Agent", USER_AGENT)

                when (val code = connection.responseCode) {
                    HttpURLConnection.HTTP_OK -> parseResponse(
                        request = request,
                        body = connection.inputStream.bufferedReader().use { it.readText() },
                    )
                    HttpURLConnection.HTTP_NOT_FOUND ->
                        LyricsProviderResult.NotFound("LRCLIB has no matching lyrics")
                    HTTP_TOO_MANY_REQUESTS -> LyricsProviderResult.RateLimited(
                        retryAfterMs = connection.getHeaderField("Retry-After")
                            ?.toLongOrNull()
                            ?.times(1_000L),
                    )
                    in 500..599 -> LyricsProviderResult.Unavailable(
                        "LRCLIB returned HTTP $code",
                    )
                    else -> LyricsProviderResult.Failed(
                        message = "LRCLIB returned HTTP $code",
                        recoverable = false,
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: IOException) {
                LyricsProviderResult.Unavailable(
                    error.message ?: "LRCLIB network request failed",
                )
            } catch (error: Exception) {
                LyricsProviderResult.Failed(
                    message = error.message ?: "Invalid LRCLIB response",
                    recoverable = false,
                )
            } finally {
                connection.disconnect()
            }
        }

    private fun parseResponse(request: LyricsRequest, body: String): LyricsProviderResult {
        val json = JSONObject(body)
        val remoteId = json.optLong("id").takeIf { it > 0L }?.toString()
        val synced = json.nullableString("syncedLyrics")
        val plain = json.nullableString("plainLyrics")
        val input = synced?.takeIf(String::isNotBlank)
            ?: plain?.takeIf(String::isNotBlank)
            ?: return LyricsProviderResult.NotFound("LRCLIB response contains no lyrics")
        val source = LyricsSource(
            providerId = descriptor.id,
            remoteId = remoteId,
            attribution = "Lyrics provided by LRCLIB",
        )
        val parseResult = LrcLyricsParser().parse(
            input = input,
            context = LyricsParseContext(
                documentId = "lrclib:${remoteId ?: stableId(body)}",
                trackKey = request.trackKey,
                source = source,
            ),
        )
        val document = parseResult.document
            ?: return LyricsProviderResult.Failed(
                message = parseResult.diagnostics.joinToString { it.message }
                    .ifBlank { "LRCLIB lyrics could not be parsed" },
                recoverable = false,
            )
        return LyricsProviderResult.Found(document, confidence = 0.9)
    }

    private fun buildUrl(request: LyricsRequest): String = buildString {
        append(endpoint)
        append("?track_name=")
        append(encode(request.title))
        append("&artist_name=")
        append(encode(request.artists.joinToString(", ")))
        request.album?.takeIf(String::isNotBlank)?.let {
            append("&album_name=")
            append(encode(it))
        }
        request.durationMs?.let {
            append("&duration=")
            append((it / 1_000.0).toInt())
        }
    }

    private fun JSONObject.nullableString(name: String): String? =
        if (isNull(name)) null else optString(name).takeIf(String::isNotBlank)

    private fun encode(value: String): String =
        URLEncoder.encode(value, Charsets.UTF_8.name())

    private fun stableId(value: String): String =
        value.hashCode().toUInt().toString(16)

    companion object {
        const val DEFAULT_ENDPOINT = "https://lrclib.net/api/get"
        private const val USER_AGENT = "YetaSimpleLyrics/1.0 (Android)"
        private const val CONNECT_TIMEOUT_MS = 10_000
        private const val READ_TIMEOUT_MS = 15_000
        private const val HTTP_TOO_MANY_REQUESTS = 429
    }
}
