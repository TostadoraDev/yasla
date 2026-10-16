package xyz.tosdev.yetasimplelyicsapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CueBoundarySchedulerTest {
    @Test
    fun schedulesOnlyTheNextSemanticBoundary() {
        assertEquals(
            500L,
            nextCueBoundaryDelayMs(longArrayOf(1_000L, 1_500L, 2_000L), 1_000L, 1f),
        )
    }

    @Test
    fun adjustsTheOneShotDelayForPlaybackSpeed() {
        assertEquals(
            250L,
            nextCueBoundaryDelayMs(longArrayOf(1_500L), 1_000L, 2f),
        )
    }

    @Test
    fun remainsIdleWithoutAPlayableFutureBoundary() {
        assertNull(nextCueBoundaryDelayMs(longArrayOf(), 1_000L, 1f))
        assertNull(nextCueBoundaryDelayMs(longArrayOf(1_000L), 1_000L, 1f))
        assertNull(nextCueBoundaryDelayMs(longArrayOf(2_000L), 1_000L, 0f))
    }
}
