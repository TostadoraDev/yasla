package xyz.tosdev.yetasimplelyicsapp.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

internal fun shouldKeepScreenOn(
    preferenceEnabled: Boolean,
    destination: AppDestination,
    lifecycleState: Lifecycle.State,
): Boolean = preferenceEnabled &&
    destination == AppDestination.NOW_PLAYING &&
    lifecycleState.isAtLeast(Lifecycle.State.RESUMED)

@Composable
internal fun KeepScreenOnEffect(
    preferenceEnabled: Boolean,
    destination: AppDestination,
    onKeepScreenOnChanged: (Boolean) -> Unit,
) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val currentCallback by rememberUpdatedState(onKeepScreenOnChanged)

    DisposableEffect(lifecycle, preferenceEnabled, destination) {
        fun updateWindowFlag() {
            currentCallback(
                shouldKeepScreenOn(
                    preferenceEnabled = preferenceEnabled,
                    destination = destination,
                    lifecycleState = lifecycle.currentState,
                ),
            )
        }

        val observer = LifecycleEventObserver { _, _ -> updateWindowFlag() }
        lifecycle.addObserver(observer)
        updateWindowFlag()

        onDispose {
            lifecycle.removeObserver(observer)
            currentCallback(false)
        }
    }
}
