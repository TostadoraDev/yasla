package xyz.tosdev.yetasimplelyicsapp.domain.lyrics

data class LyricsRequest(
    val trackKey: String,
    val title: String,
    val artists: List<String>,
    val album: String? = null,
    val durationMs: Long? = null,
    val languageTags: List<String> = emptyList(),
) {
    init {
        require(trackKey.isNotBlank()) { "trackKey must not be blank" }
        require(title.isNotBlank()) { "title must not be blank" }
        require(artists.isNotEmpty()) { "artists must not be empty" }
        require(durationMs == null || durationMs >= 0L) { "durationMs must be non-negative" }
    }
}

data class LyricsProviderDescriptor(
    val id: String,
    val displayName: String,
    val capabilities: Set<LyricsCapability>,
    val requiresNetwork: Boolean,
) {
    init {
        require(id.isNotBlank()) { "provider id must not be blank" }
        require(displayName.isNotBlank()) { "provider displayName must not be blank" }
    }
}

sealed interface LyricsProviderResult {
    data class Found(
        val document: LyricsDocument,
        val confidence: Double,
    ) : LyricsProviderResult {
        init {
            require(confidence in 0.0..1.0) { "confidence must be between 0 and 1" }
        }
    }

    data class NotFound(val reason: String? = null) : LyricsProviderResult

    data class Unavailable(val reason: String) : LyricsProviderResult

    data class RateLimited(val retryAfterMs: Long? = null) : LyricsProviderResult {
        init {
            require(retryAfterMs == null || retryAfterMs >= 0L) {
                "retryAfterMs must be non-negative"
            }
        }
    }

    data class Failed(
        val message: String,
        val recoverable: Boolean,
    ) : LyricsProviderResult
}

interface LyricsProvider {
    val descriptor: LyricsProviderDescriptor

    suspend fun findLyrics(request: LyricsRequest): LyricsProviderResult
}
