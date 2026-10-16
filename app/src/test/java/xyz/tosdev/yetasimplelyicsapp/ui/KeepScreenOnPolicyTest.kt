package xyz.tosdev.yetasimplelyicsapp.ui

import androidx.lifecycle.Lifecycle
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KeepScreenOnPolicyTest {
    @Test
    fun `setting defaults to enabled`() {
        assertTrue(SettingsUiState().keepScreenAwake)
    }

    @Test
    fun `keeps screen on only for resumed Now Playing`() {
        assertTrue(
            shouldKeepScreenOn(
                preferenceEnabled = true,
                destination = AppDestination.NOW_PLAYING,
                lifecycleState = Lifecycle.State.RESUMED,
            ),
        )

        assertFalse(
            shouldKeepScreenOn(
                preferenceEnabled = true,
                destination = AppDestination.SETTINGS,
                lifecycleState = Lifecycle.State.RESUMED,
            ),
        )
        assertFalse(
            shouldKeepScreenOn(
                preferenceEnabled = true,
                destination = AppDestination.NOW_PLAYING,
                lifecycleState = Lifecycle.State.STARTED,
            ),
        )
        assertFalse(
            shouldKeepScreenOn(
                preferenceEnabled = false,
                destination = AppDestination.NOW_PLAYING,
                lifecycleState = Lifecycle.State.RESUMED,
            ),
        )
    }
}
