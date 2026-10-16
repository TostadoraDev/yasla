package xyz.tosdev.yetasimplelyicsapp

import android.animation.ValueAnimator
import android.Manifest
import android.content.pm.PackageManager
import android.content.Context
import android.os.Build
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.material3.SnackbarHostState
import androidx.core.view.WindowCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.runtime.getValue
import kotlinx.coroutines.flow.collectLatest
import xyz.tosdev.yetasimplelyicsapp.overlay.LyricsOverlayController
import xyz.tosdev.yetasimplelyicsapp.localization.AppLanguageManager
import xyz.tosdev.yetasimplelyicsapp.ui.AdvancedLyricsApp
import xyz.tosdev.yetasimplelyicsapp.ui.LyricsAppActions

private const val MAIN_ACTIVITY_TAG = "MainActivity"

class MainActivity : ComponentActivity() {
    private val container get() = (application as LyricsApplication).container
    private val importDocuments = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) container.importDocuments(uris.distinct())
    }
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        container.notification(granted)
        if (!granted) container.messages.tryEmit(getString(R.string.system_notifications_disabled))
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLanguageManager.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        splashScreen.setOnExitAnimationListener { provider ->
            val splashView = provider.view
            val duration = if (
                Build.VERSION.SDK_INT < Build.VERSION_CODES.O || ValueAnimator.areAnimatorsEnabled()
            ) 180L else 0L
            splashView.animate()
                .alpha(0f)
                .scaleX(1.055f)
                .scaleY(1.055f)
                .setDuration(duration)
                .withEndAction(provider::remove)
                .start()
        }
        val container = (application as LyricsApplication).container
        enableEdgeToEdge()
        setContent {
            val state by container.state.collectAsStateWithLifecycle()
            val snackbarHostState = remember { SnackbarHostState() }
            LaunchedEffect(Unit) {
                container.messages.collectLatest { message ->
                    snackbarHostState.showSnackbar(message)
                }
            }

            AdvancedLyricsApp(
                state = state,
                onKeepScreenOnChanged = { enabled ->
                    if (enabled) {
                        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    } else {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    }
                },
                onDarkSurfaceChanged = { dark ->
                    WindowCompat.getInsetsController(window, window.decorView).apply {
                        isAppearanceLightStatusBars = !dark
                        isAppearanceLightNavigationBars = !dark
                    }
                },
                snackbarHostState = snackbarHostState,
                actions = LyricsAppActions(
                    onSearch = container::search,
                    onOpenLibraryItem = container::openLibrary,
                    onSkipPrevious = container::skipPrevious,
                    onTogglePlayback = container::togglePlayback,
                    onSkipNext = container::skipNext,
                    onSeek = container::seek,
                    onSeekLine = container::seekLine,
                    onRetryLyrics = container::retryLyrics,
                    onOffsetChange = container::offset,
                    onEditorSave = container::saveEditor,
                    onSettingsChange = container::settings,
                    onOverlayChange = container::overlay,
                    onWidgetChange = container::widget,
                    onImportLyrics = {
                        try {
                            // Some document providers classify TTML as an unknown binary type.
                            importDocuments.launch(arrayOf("*/*"))
                        } catch (_: android.content.ActivityNotFoundException) {
                            container.messages.tryEmit(getString(R.string.system_file_picker_unavailable))
                        }
                    },
                    onExportLyrics = {
                        val text = container.exportText()
                        if (text.isNotBlank()) {
                            // Copy to clipboard or share
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, text)
                            }
                            startActivity(Intent.createChooser(intent, getString(R.string.system_export_lrc)))
                        }
                    },
                    onExportTtml = {
                        val xml = container.exportTtml()
                        if (xml != null) {
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/xml"
                                putExtra(Intent.EXTRA_TEXT, xml)
                            }
                            startActivity(Intent.createChooser(intent, getString(R.string.system_export_ttml)))
                        }
                    },
                    onRequestMediaAccess = {
                        startActivity(container.media.notificationAccessSettingsIntent())
                    },
                    onRequestOverlayAccess = {
                        startActivity(LyricsOverlayController.permissionIntent(this))
                    },
                    onToggleLyricsNotification = { enabled ->
                        if (enabled && Build.VERSION.SDK_INT >= 33 &&
                            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else container.notification(enabled)
                    },
                    onLanguageChange = { tag ->
                        if (AppLanguageManager.apply(this, tag)) {
                            container.refreshLocalizedSurfaces()
                            recreate()
                        }
                    },
                )
            )
        }
    }

    override fun onStart() {
        super.onStart()
        (application as LyricsApplication).container.setForeground(true)
    }

    override fun onPause() {
        debugTrace(applicationContext, MAIN_ACTIVITY_TAG) { "Activity.onPause" }
        container.media.traceLiveness("Activity.onPause")
        super.onPause()
    }

    override fun onStop() {
        debugTrace(applicationContext, MAIN_ACTIVITY_TAG) { "Activity.onStop" }
        super.onStop()
        container.setForeground(false)
        container.media.traceLiveness("Activity.onStop")
    }

    override fun onDestroy() {
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        super.onDestroy()
    }
}
