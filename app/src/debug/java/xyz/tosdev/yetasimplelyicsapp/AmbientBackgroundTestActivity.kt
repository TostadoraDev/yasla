package xyz.tosdev.yetasimplelyicsapp

import android.graphics.Rect
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import kotlin.math.roundToInt
import xyz.tosdev.yetasimplelyicsapp.ui.MotionLevel
import xyz.tosdev.yetasimplelyicsapp.ui.designsystem.DynamicProceduralBackground
import xyz.tosdev.yetasimplelyicsapp.ui.designsystem.rememberAmbientAnimationsEnabled
import xyz.tosdev.yetasimplelyicsapp.ui.theme.ArtworkAmbientColors
import xyz.tosdev.yetasimplelyicsapp.ui.theme.LyricsPalette
import xyz.tosdev.yetasimplelyicsapp.ui.theme.PaletteSource
import androidx.compose.ui.graphics.Color

/** Debug-only host for the production background. No fake clock or replacement renderer. */
class AmbientBackgroundTestActivity : ComponentActivity() {
    private var level by mutableStateOf(MotionLevel.NORMAL)
    private var palette by mutableStateOf(WarmPalette)
    private var paired by mutableStateOf(false)
    private var probingSystemMotion by mutableStateOf(false)
    private val probeOwner = ProbeOwner()
    private var probeReadEnabled = false

    @Volatile var parentCompositionCount: Int = 0
        private set
    @Volatile var observedProbeEnabled: Boolean? = null
        private set
    @Volatile var targetBounds: Rect? = null
        private set
    @Volatile var referenceBounds: Rect? = null
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SideEffect { parentCompositionCount++ }
            if (probingSystemMotion) {
                val enabled = rememberAmbientAnimationsEnabled(probeOwner.lifecycle) { probeReadEnabled }
                SideEffect { observedProbeEnabled = enabled }
            }
            BoxWithConstraints(Modifier.fillMaxSize()) {
                if (paired) {
                    val panelWidth = with(LocalDensity.current) { (maxWidth.roundToPx() / 2).toDp() }
                    Row(Modifier.fillMaxSize()) {
                        Box(Modifier.requiredWidth(panelWidth).fillMaxHeight()) {
                            DynamicProceduralBackground(palette, level, Modifier.fillMaxSize().onGloballyPositioned {
                                targetBounds = it.boundsInWindow().let { r -> Rect(r.left.roundToInt(), r.top.roundToInt(), r.right.roundToInt(), r.bottom.roundToInt()) }
                            })
                        }
                        Box(Modifier.requiredWidth(panelWidth).fillMaxHeight()) {
                            DynamicProceduralBackground(WarmPalette, level, Modifier.fillMaxSize().onGloballyPositioned {
                                referenceBounds = it.boundsInWindow().let { r -> Rect(r.left.roundToInt(), r.top.roundToInt(), r.right.roundToInt(), r.bottom.roundToInt()) }
                            })
                        }
                    }
                } else {
                    DynamicProceduralBackground(palette, level, Modifier.fillMaxSize().onGloballyPositioned {
                        targetBounds = it.boundsInWindow().let { r -> Rect(r.left.roundToInt(), r.top.roundToInt(), r.right.roundToInt(), r.bottom.roundToInt()) }
                    })
                }
            }
        }
    }

    fun setMotionLevel(value: MotionLevel) { level = value }
    fun updatePalette(value: LyricsPalette) { palette = value }
    fun showPairedPanels() { paired = true }

    fun showSystemMotionProbe() {
        probeReadEnabled = false
        probeOwner.registry.currentState = Lifecycle.State.RESUMED
        probingSystemMotion = true
    }

    fun resumeProbeWithAnimationsEnabled() {
        probeOwner.registry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        probeOwner.registry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        probeReadEnabled = true
        probeOwner.registry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        probeOwner.registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    private class ProbeOwner : LifecycleOwner {
        val registry = LifecycleRegistry(this)
        override val lifecycle: Lifecycle get() = registry
    }

    companion object {
        val WarmPalette = LyricsPalette(
            primary = Color(0xFFFFA58A), secondary = Color(0xFFAA6B4C), tertiary = Color(0xFF76513F),
            background = Color(0xFF171515), surface = Color(0xFF24201E),
            onBackground = Color.White, onSurfaceMuted = Color(0xFFBCB3AE),
            source = PaletteSource.ARTWORK, cacheKey = 0x21436587,
            ambient = ArtworkAmbientColors(Color(0xFFDB7732), Color(0xFF8E4230), Color(0xFF573B2C), Color(0xFF100C09)),
        )
    }
}
