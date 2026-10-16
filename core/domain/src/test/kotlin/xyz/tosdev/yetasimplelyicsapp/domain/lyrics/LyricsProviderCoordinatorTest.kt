package xyz.tosdev.yetasimplelyicsapp.domain.lyrics

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class LyricsProviderCoordinatorTest {
    @Test
    fun adequateLocalResultPreventsNetworkRequest() = runTest {
        var networkCalls = 0
        val local = FakeProvider("local", requiresNetwork = false) {
            LyricsProviderResult.Found(document("local"), confidence = 0.95)
        }
        val network = FakeProvider("network", requiresNetwork = true) {
            networkCalls += 1
            LyricsProviderResult.Found(document("network"), confidence = 1.0)
        }

        val result = LyricsProviderCoordinator(listOf(network, local)).findBest(
            request = request(),
            policy = LyricsLookupPolicy(providerOrder = listOf("local", "network")),
        )

        assertEquals("local", result.selected?.document?.source?.providerId)
        assertEquals(0, networkCalls)
    }

    @Test
    fun offlinePolicySkipsNetworkProviders() = runTest {
        val network = FakeProvider("network", requiresNetwork = true) {
            error("must not be called")
        }

        val result = LyricsProviderCoordinator(listOf(network)).findBest(
            request = request(),
            policy = LyricsLookupPolicy(allowNetwork = false),
        )

        assertEquals(0, result.attempts.size)
        assertEquals(null, result.selected)
    }

    @Test
    fun lowConfidenceLocalResultCanBeReplacedByNetwork() = runTest {
        val local = FakeProvider("local", false) {
            LyricsProviderResult.Found(document("local"), confidence = 0.4)
        }
        val network = FakeProvider("network", true) {
            LyricsProviderResult.Found(document("network"), confidence = 0.9)
        }

        val result = LyricsProviderCoordinator(listOf(local, network)).findBest(
            request = request(),
            policy = LyricsLookupPolicy(),
        )

        assertNotNull(result.selected)
        assertEquals("network", result.selected?.document?.source?.providerId)
    }

    @Test
    fun providerCancellationIsRethrown() = runTest {
        val cancelling = FakeProvider("cancel", requiresNetwork = false) {
            throw CancellationException("cancel lookup")
        }

        try {
            LyricsProviderCoordinator(listOf(cancelling)).findBest(
                request = request(),
                policy = LyricsLookupPolicy(),
            )
            throw AssertionError("Expected provider cancellation to propagate")
        } catch (_: CancellationException) {
            // Structured provider failures must never consume coroutine cancellation.
        }
    }

    private fun request() = LyricsRequest(
        trackKey = "track",
        title = "Title",
        artists = listOf("Artist"),
    )

    private fun document(providerId: String) = LyricsDocument(
        id = providerId,
        trackKey = "track",
        source = LyricsSource(providerId),
        syncType = SyncType.PLAIN,
        lines = listOf(
            LyricsLine(
                id = "line",
                order = 0,
                text = LocalizedText("Text"),
            ),
        ),
    )

    private class FakeProvider(
        id: String,
        requiresNetwork: Boolean,
        private val answer: suspend () -> LyricsProviderResult,
    ) : LyricsProvider {
        override val descriptor = LyricsProviderDescriptor(
            id = id,
            displayName = id,
            capabilities = setOf(LyricsCapability.PLAIN_TEXT),
            requiresNetwork = requiresNetwork,
        )

        override suspend fun findLyrics(request: LyricsRequest): LyricsProviderResult = answer()
    }
}
