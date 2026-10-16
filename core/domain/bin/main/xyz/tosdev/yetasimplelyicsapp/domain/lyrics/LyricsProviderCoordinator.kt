package xyz.tosdev.yetasimplelyicsapp.domain.lyrics

import kotlinx.coroutines.CancellationException

data class LyricsLookupPolicy(
    val providerOrder: List<String> = emptyList(),
    val allowNetwork: Boolean = true,
    val acceptableLocalConfidence: Double = 0.86,
) {
    init {
        require(acceptableLocalConfidence in 0.0..1.0)
    }
}

data class LyricsProviderAttempt(
    val providerId: String,
    val result: LyricsProviderResult,
)

data class LyricsLookupSummary(
    val selected: LyricsProviderResult.Found?,
    val attempts: List<LyricsProviderAttempt>,
)

class LyricsProviderCoordinator(
    providers: List<LyricsProvider>,
) {
    private val providersById = providers.associateBy { it.descriptor.id }

    suspend fun findBest(
        request: LyricsRequest,
        policy: LyricsLookupPolicy,
    ): LyricsLookupSummary {
        val ordered = buildList {
            policy.providerOrder.forEach { id -> providersById[id]?.let(::add) }
            providersById.values
                .filterNot { provider -> any { it.descriptor.id == provider.descriptor.id } }
                .sortedBy { it.descriptor.requiresNetwork }
                .forEach(::add)
        }

        val attempts = mutableListOf<LyricsProviderAttempt>()
        var best: LyricsProviderResult.Found? = null

        for (provider in ordered) {
            if (provider.descriptor.requiresNetwork && !policy.allowNetwork) continue
            val result = try {
                provider.findLyrics(request)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                LyricsProviderResult.Failed(
                    message = error.message ?: error::class.simpleName.orEmpty(),
                    recoverable = true,
                )
            }
            attempts += LyricsProviderAttempt(provider.descriptor.id, result)

            if (result is LyricsProviderResult.Found) {
                if (best == null || result.confidence > best.confidence) best = result
                if (!provider.descriptor.requiresNetwork &&
                    result.confidence >= policy.acceptableLocalConfidence
                ) {
                    break
                }
            }
        }

        return LyricsLookupSummary(
            selected = best,
            attempts = attempts,
        )
    }
}
