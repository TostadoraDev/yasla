package xyz.tosdev.yetasimplelyicsapp

import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.os.SystemClock
import android.os.PowerManager
import java.io.FileInputStream
import androidx.activity.ComponentActivity
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class KeepScreenOnInstrumentedTest {
    @Test
    fun flagFollowsNowPlayingPreferenceAndResumedLifecycle() {
        ActivityScenario.launch(KeepScreenOnTestActivity::class.java).use { scenario ->
            awaitFlag(scenario, expected = true)

            scenario.moveToState(Lifecycle.State.STARTED)
            awaitFlag(scenario, expected = false)

            scenario.moveToState(Lifecycle.State.RESUMED)
            awaitFlag(scenario, expected = true)

            click(scenario, R.string.ui_nav_settings)
            awaitFlag(scenario, expected = false)

            click(scenario, R.string.ui_nav_now_playing)
            awaitFlag(scenario, expected = true)

            scenario.onActivity {
                it.settings = it.settings.copy(keepScreenAwake = false)
            }
            awaitFlag(scenario, expected = false)
        }
    }

    private fun ComponentActivity.isKeepingScreenOn(): Boolean =
        window.attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON != 0

    private fun click(
        scenario: ActivityScenario<KeepScreenOnTestActivity>,
        labelRes: Int,
    ) {
        scenario.onActivity { activity ->
            val label = activity.getString(labelRes)
            val action = nodes(activity).firstNotNullOfOrNull { node ->
                node.config.getOrNull(SemanticsActions.OnClick)?.action
                    ?.takeIf { nodeTexts(node).any { it == label } }
            }
            assertTrue("Missing interactive label: $label", action?.invoke() == true)
        }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        SystemClock.sleep(100)
    }

    @Test
    fun screenOutlivesConfiguredTimeoutOnlyOnNowPlaying() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        fun shell(command: String): String = instrumentation.uiAutomation.executeShellCommand(command).use {
            FileInputStream(it.fileDescriptor).use { stream -> stream.readBytes().toString(Charsets.UTF_8).trim() }
        }
        val timeout = shell("settings get system screen_off_timeout")
        val stayOn = shell("settings get global stay_on_while_plugged_in")
        try {
            // Android enforces a device minimum (10s on the test AVD). Test above that minimum.
            shell("settings put system screen_off_timeout 15000")
            shell("settings put global stay_on_while_plugged_in 0")
            shell("dumpsys battery unplug")
            shell("input keyevent KEYCODE_WAKEUP")
            ActivityScenario.launch(KeepScreenOnTestActivity::class.java).use { scenario ->
                awaitFlag(scenario, expected = true)
                SystemClock.sleep(18_000)
                val power = instrumentation.targetContext.getSystemService(PowerManager::class.java)
                assertTrue("Now Playing must outlive screen timeout", power.isInteractive)
                click(scenario, R.string.ui_nav_settings)
                awaitFlag(scenario, expected = false)
                SystemClock.sleep(18_000)
                assertFalse("Settings must restore normal screen timeout", power.isInteractive)
                shell("input keyevent KEYCODE_WAKEUP")
            }
        } finally {
            shell("input keyevent KEYCODE_WAKEUP")
            shell("settings put system screen_off_timeout $timeout")
            shell("settings put global stay_on_while_plugged_in $stayOn")
            shell("dumpsys battery reset")
        }
    }

    private fun awaitFlag(
        scenario: ActivityScenario<KeepScreenOnTestActivity>,
        expected: Boolean,
    ) {
        val deadline = SystemClock.elapsedRealtime() + 5_000L
        var actual = !expected
        while (SystemClock.elapsedRealtime() < deadline) {
            scenario.onActivity { actual = it.isKeepingScreenOn() }
            if (actual == expected) return
            SystemClock.sleep(25L)
        }
        assertTrue("Expected FLAG_KEEP_SCREEN_ON=$expected but was $actual", actual == expected)
    }

    private fun nodes(activity: ComponentActivity): List<SemanticsNode> {
        val owner = checkNotNull(root(activity.window.decorView))
        owner.measureAndLayoutForTest()
        fun collect(node: SemanticsNode): List<SemanticsNode> =
            listOf(node) + node.children.flatMap(::collect)
        return collect(owner.semanticsOwner.rootSemanticsNode)
    }

    private fun nodeTexts(node: SemanticsNode): List<String> =
        node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } +
            node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty()

    private fun root(view: View): ViewRootForTest? {
        if (view is ViewRootForTest) return view
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                root(view.getChildAt(index))?.let { return it }
            }
        }
        return null
    }
}
