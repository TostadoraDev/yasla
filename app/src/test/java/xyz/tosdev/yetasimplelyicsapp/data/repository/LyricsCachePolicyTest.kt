package xyz.tosdev.yetasimplelyicsapp.data.repository

import kotlin.time.Duration.Companion.days
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsCachePolicyTest {
    private val policy = LyricsCachePolicy(remoteTimeToLive = 7.days)

    @Test
    fun remoteLyricsExpireAfterConfiguredTtl() {
        assertEquals(604_801_000L, policy.expiresAt(1_000L, isLocalImport = false))
        assertTrue(policy.isFresh(2_000L, 2_000L))
        assertFalse(policy.isFresh(1_999L, 2_000L))
    }

    @Test
    fun localImportsNeverExpire() {
        assertEquals(Long.MAX_VALUE, policy.expiresAt(1_000L, isLocalImport = true))
    }

    @Test
    fun expirySaturatesInsteadOfOverflowing() {
        assertEquals(
            Long.MAX_VALUE,
            policy.expiresAt(Long.MAX_VALUE - 1_000L, isLocalImport = false),
        )
    }
}
