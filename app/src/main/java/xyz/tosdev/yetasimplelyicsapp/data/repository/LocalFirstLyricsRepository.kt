package xyz.tosdev.yetasimplelyicsapp.data.repository

import kotlinx.coroutines.flow.first
import xyz.tosdev.yetasimplelyicsapp.data.local.CachedLyrics
import xyz.tosdev.yetasimplelyicsapp.data.local.LyricsTrackMetadata
import xyz.tosdev.yetasimplelyicsapp.data.local.RoomLyricsStore
import xyz.tosdev.yetasimplelyicsapp.data.preferences.AppPreferences
import xyz.tosdev.yetasimplelyicsapp.domain.lyrics.LyricsDocument
import xyz.tosdev.yetasimplelyicsapp.domain.lyrics.LyricsLookupPolicy
import xyz.tosdev.yetasimplelyicsapp.domain.lyrics.LyricsProvider
import xyz.tosdev.yetasimplelyicsapp.domain.lyrics.LyricsProviderCoordinator
import xyz.tosdev.yetasimplelyicsapp.domain.lyrics.LyricsProviderResult
import xyz.tosdev.yetasimplelyicsapp.domain.lyrics.LyricsRequest

enum class LyricsOrigin {
    CACHE,
    PROVIDER,
}

enum class CacheFreshness {
    FRESH,
    STALE,
}

sealed interface LyricsRepositoryResult {
    data class Found(
        val document: LyricsDocument,
        val origin: LyricsOrigin,
        val freshness: CacheFreshness,
    ) : LyricsRepositoryResult

    data object NotFound : LyricsRepositoryResult
    data class RateLimited(val retryAfterMs: Long?) : LyricsRepositoryResult
    data class Unavailable(val reason: String) : LyricsRepositoryResult
    data class Failed(val message: String, val recoverable: Boolean) : LyricsRepositoryResult
}

class LocalFirstLyricsRepository(
    private val store: RoomLyricsStore,
    providers: List<LyricsProvider>,
    private val preferences: AppPreferences,
    private val cachePolicy: LyricsCachePolicy = LyricsCachePolicy(),
    private val nowEpochMs: () -> Long = System::currentTimeMillis,
) {
    private val coordinator = LyricsProviderCoordinator(providers)

    suspend fun findLyrics(
        request: LyricsRequest,
        forceRefresh: Boolean = false,
        allowNetwork: Boolean = true,
    ): LyricsRepositoryResult {
        val now = nowEpochMs()
        val cached = store.find(request.trackKey)
        if (!forceRefresh && cached != null && cachePolicy.isFresh(cached.expiresAtEpochMs, now)) {
            return cached.toFound(CacheFreshness.FRESH)
        }

        val summary = coordinator.findBest(
            request = request,
            policy = LyricsLookupPolicy(
                providerOrder = preferences.providerPriority.first(),
                allowNetwork = allowNetwork,
            ),
        )
        summary.selected?.let { selected ->
            val isLocal = selected.document.source.providerId == "local"
            store.save(
                document = selected.document,
                cachedAtEpochMs = now,
                expiresAtEpochMs = cachePolicy.expiresAt(now, isLocal),
                metadata = LyricsTrackMetadata(
                    title = request.title,
                    artists = request.artists,
                    album = request.album,
                    durationMs = request.durationMs,
                    artworkUri = request.artworkUri,
                ),
            )
            return LyricsRepositoryResult.Found(
                document = selected.document,
                origin = LyricsOrigin.PROVIDER,
                freshness = CacheFreshness.FRESH,
            )
        }

        if (cached != null) return cached.toFound(CacheFreshness.STALE)

        val results = summary.attempts.map { it.result }
        results.filterIsInstance<LyricsProviderResult.RateLimited>().firstOrNull()?.let {
            return LyricsRepositoryResult.RateLimited(it.retryAfterMs)
        }
        results.filterIsInstance<LyricsProviderResult.Unavailable>().firstOrNull()?.let {
            return LyricsRepositoryResult.Unavailable(it.reason)
        }
        results.filterIsInstance<LyricsProviderResult.Failed>().firstOrNull()?.let {
            return LyricsRepositoryResult.Failed(it.message, it.recoverable)
        }
        return LyricsRepositoryResult.NotFound
    }

    suspend fun removeExpiredCache(): Int = store.deleteExpired(nowEpochMs())

    private fun CachedLyrics.toFound(freshness: CacheFreshness) =
        LyricsRepositoryResult.Found(
            document = document,
            origin = LyricsOrigin.CACHE,
            freshness = freshness,
        )
}
