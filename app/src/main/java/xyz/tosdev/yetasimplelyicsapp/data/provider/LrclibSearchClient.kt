package xyz.tosdev.yetasimplelyicsapp.data.provider

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import xyz.tosdev.yetasimplelyicsapp.domain.lyrics.LyricsProviderResult
import xyz.tosdev.yetasimplelyicsapp.domain.lyrics.LyricsRequest
import xyz.tosdev.yetasimplelyicsapp.domain.lyrics.LyricsSource
import xyz.tosdev.yetasimplelyicsapp.domain.lyrics.parser.LrcLyricsParser
import xyz.tosdev.yetasimplelyicsapp.domain.lyrics.parser.LyricsParseContext

data class LrclibCandidate(
    val remoteId: Long,
    val trackName: String,
    val artistName: String,
    val albumName: String?,
    val durationSeconds: Double?,
    val instrumental: Boolean,
    val plainLyrics: String?,
    val syncedLyrics: String?,
)

sealed interface LrclibSearchResult {
    data class Success(val candidates: List<LrclibCandidate>) : LrclibSearchResult
    data class RateLimited(val retryAfterMs: Long?) : LrclibSearchResult
    data class Unavailable(val reason: String) : LrclibSearchResult
    data class Failed(val message: String) : LrclibSearchResult
}

class LrclibSearchClient(
    private val endpoint: String = DEFAULT_ENDPOINT,
    private val connectionFactory: (URL) -> HttpURLConnection = {
        it.openConnection() as HttpURLConnection
    },
) {
    suspend fun search(request: LyricsRequest): LrclibSearchResult =
        withContext(Dispatchers.IO) {
            val connection = connectionFactory(URL(buildUrl(request)))
            try {
                connection.requestMethod = "GET"
                connection.connectTimeout = CONNECT_TIMEOUT_MS
                connection.readTimeout = READ_TIMEOUT_MS
                connection.setRequestProperty("Accept", "application/json")
                connection.setRequestProperty("User-Agent", USER_AGENT)
                when (val code = connection.responseCode) {
                    HttpURLConnection.HTTP_OK -> {
                        val body = connection.inputStream.bufferedReader().use { it.readText() }
                        LrclibSearchResult.Success(parseCandidates(body))
                    }
                    HTTP_TOO_MANY_REQUESTS -> LrclibSearchResult.RateLimited(
                        connection.getHeaderField("Retry-After")?.toLongOrNull()?.times(1_000L),
                    )
                    in 500..599 -> LrclibSearchResult.Unavailable("LRCLIB returned HTTP $code")
                    else -> LrclibSearchResult.Failed("LRCLIB returned HTTP $code")
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: IOException) {
                LrclibSearchResult.Unavailable(
                    error.message ?: "LRCLIB search request failed",
                )
            } catch (error: Exception) {
                LrclibSearchResult.Failed(error.message ?: "Invalid LRCLIB search response")
            } finally {
                connection.disconnect()
            }
        }

    fun select(candidate: LrclibCandidate, request: LyricsRequest): LyricsProviderResult {
        val input = candidate.syncedLyrics?.takeIf(String::isNotBlank)
            ?: candidate.plainLyrics?.takeIf(String::isNotBlank)
            ?: return LyricsProviderResult.NotFound("The selected result has no lyrics")
        val source = LyricsSource(
            providerId = "lrclib",
            remoteId = candidate.remoteId.toString(),
            attribution = "Lyrics provided by LRCLIB",
        )
        val parsed = LrcLyricsParser().parse(
            input,
            LyricsParseContext(
                documentId = "lrclib:${candidate.remoteId}",
                trackKey = request.trackKey,
                source = source,
            ),
        )
        return parsed.document?.let { LyricsProviderResult.Found(it, confidence = 1.0) }
            ?: LyricsProviderResult.Failed(
                parsed.diagnostics.joinToString { it.message }
                    .ifBlank { "The selected lyrics could not be parsed" },
                recoverable = false,
            )
    }

    private fun parseCandidates(body: String): List<LrclibCandidate> {
        val json = JSONArray(body)
        return buildList {
            for (index in 0 until json.length()) {
                val item = json.optJSONObject(index) ?: continue
                add(
                    LrclibCandidate(
                        remoteId = item.optLong("id"),
                        trackName = item.optString("trackName"),
                        artistName = item.optString("artistName"),
                        albumName = item.nullableString("albumName"),
                        durationSeconds = item.optDouble("duration")
                            .takeUnless(Double::isNaN),
                        instrumental = item.optBoolean("instrumental"),
                        plainLyrics = item.nullableString("plainLyrics"),
                        syncedLyrics = item.nullableString("syncedLyrics"),
                    ),
                )
            }
        }
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
    }

    private fun JSONObject.nullableString(name: String): String? =
        if (isNull(name)) null else optString(name).takeIf(String::isNotBlank)

    private fun encode(value: String): String =
        URLEncoder.encode(value, Charsets.UTF_8.name())

    companion object {
        const val DEFAULT_ENDPOINT = "https://lrclib.net/api/search"
        private const val USER_AGENT = "YetaSimpleLyrics/1.0 (Android)"
        private const val CONNECT_TIMEOUT_MS = 10_000
        private const val READ_TIMEOUT_MS = 15_000
        private const val HTTP_TOO_MANY_REQUESTS = 429
    }
}
