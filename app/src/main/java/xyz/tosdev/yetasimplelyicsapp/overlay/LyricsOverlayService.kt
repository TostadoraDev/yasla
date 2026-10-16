package xyz.tosdev.yetasimplelyicsapp.overlay

import android.annotation.SuppressLint
import android.app.KeyguardManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import xyz.tosdev.yetasimplelyicsapp.notification.LyricsNotificationCoordinator
import xyz.tosdev.yetasimplelyicsapp.R
import xyz.tosdev.yetasimplelyicsapp.localization.AppLanguageManager
import xyz.tosdev.yetasimplelyicsapp.ui.LyricLineUi
import xyz.tosdev.yetasimplelyicsapp.ui.MotionLevel
import xyz.tosdev.yetasimplelyicsapp.ui.components.CurrentWordLyrics
import xyz.tosdev.yetasimplelyicsapp.ui.components.KaraokeColors
import xyz.tosdev.yetasimplelyicsapp.ui.components.KaraokeText
import xyz.tosdev.yetasimplelyicsapp.ui.components.forTransparentOverlay
import xyz.tosdev.yetasimplelyicsapp.ui.components.rememberKaraokeColors
import kotlin.math.roundToInt

class LyricsOverlayService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var windowManager: WindowManager
    private lateinit var keyguardManager: KeyguardManager
    private lateinit var notificationCoordinator: LyricsNotificationCoordinator
    private var overlayView: ComposeView? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var viewTreeOwners: OverlayViewTreeOwners? = null
    private var config = OverlayConfig()
    private var lastState = OverlayLyricsState()
    private var minimalLockScreenApplied: Boolean? = null
    private val renderedState = mutableStateOf(lastState)
    private val renderedConfig = mutableStateOf(config)
    private val renderedMinimalMode = mutableStateOf(false)
    private val keyguardFinalRefresh = Runnable { render(lastState) }
    private val keyguardReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            overlayView?.removeCallbacks(keyguardFinalRefresh)
            render(lastState)
            if (
                intent?.action == Intent.ACTION_SCREEN_ON ||
                intent?.action == Intent.ACTION_USER_PRESENT
            ) {
                overlayView?.postDelayed(keyguardFinalRefresh, KEYGUARD_EVENT_SETTLE_DELAY_MS)
            }
        }
    }

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(AppLanguageManager.wrap(base))
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WindowManager::class.java)
        keyguardManager = getSystemService(KeyguardManager::class.java)
        notificationCoordinator = LyricsNotificationCoordinator(this)
        registerKeyguardReceiver()
        scope.launch {
            LyricsOverlayController.content.collectLatest { state ->
                lastState = state
                render(state)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        startForeground(
            LyricsNotificationCoordinator.OVERLAY_NOTIFICATION_ID,
            notificationCoordinator.buildOverlayServiceNotification(),
        )
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            LyricsOverlayController.report(
                OverlayRuntimeStatus.Error(
                    AppLanguageManager.localizedContext(this)
                        .getString(R.string.system_overlay_android_version),
                ),
            )
            stopSelf()
            return START_NOT_STICKY
        }
        if (!Settings.canDrawOverlays(this)) {
            LyricsOverlayController.report(OverlayRuntimeStatus.PermissionRequired)
            stopSelf()
            return START_STICKY
        }

        config = LyricsOverlayController.loadConfig(this)
        if (overlayView == null) {
            addOverlay()
        } else {
            applyConfiguration()
            render(lastState)
        }
        return START_STICKY
    }

    override fun onDestroy() {
        overlayView?.let { view ->
            view.removeCallbacks(keyguardFinalRefresh)
            view.disposeComposition()
            runCatching { windowManager.removeView(view) }
        }
        viewTreeOwners?.destroy()
        viewTreeOwners = null
        overlayView = null
        runCatching { unregisterReceiver(keyguardReceiver) }
        LyricsOverlayController.report(OverlayRuntimeStatus.Stopped)
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun addOverlay() {
        val view = createOverlayView()
        val params = createLayoutParams()
        overlayView = view
        layoutParams = params
        try {
            windowManager.addView(view, params)
            applyConfiguration()
            render(lastState)
        } catch (error: RuntimeException) {
            view.disposeComposition()
            viewTreeOwners?.destroy()
            viewTreeOwners = null
            overlayView = null
            layoutParams = null
            LyricsOverlayController.report(
                OverlayRuntimeStatus.Error(error.message ?: error.javaClass.simpleName),
            )
            stopSelf()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun createOverlayView(): ComposeView {
        val owners = OverlayViewTreeOwners().also {
            it.performRestore(null)
            it.resume()
        }
        viewTreeOwners = owners
        return ComposeView(this).apply {
            setViewTreeLifecycleOwner(owners)
            setViewTreeSavedStateRegistryOwner(owners)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            clipChildren = true
            clipToPadding = true
            setOnTouchListener(DragTouchListener())
            setContent {
                OverlayLyricsContent(
                    state = renderedState.value,
                    config = renderedConfig.value,
                    minimalLockScreen = renderedMinimalMode.value,
                )
            }
        }
    }

    private fun createLayoutParams(): WindowManager.LayoutParams {
        val prefs = getSharedPreferences(POSITION_PREFS, MODE_PRIVATE)
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            error("TYPE_APPLICATION_OVERLAY requires Android 8.0 or newer")
        }
        return WindowManager.LayoutParams(
            overlayWidthPx(),
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            baseFlags(config),
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or config.alignment.toGravity()
            x = if (config.alignment == OverlayAlignment.CENTER) 0 else prefs.getInt(KEY_X, dp(20))
            y = prefs.getInt(KEY_Y, dp(120))
            windowAnimations = 0
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                setFitInsetsTypes(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                setFitInsetsSides(WindowInsets.Side.all())
                setFitInsetsIgnoringVisibility(true)
            }
        }
    }

    private fun applyConfiguration() {
        val view = overlayView ?: return
        val params = layoutParams ?: return
        config = LyricsOverlayController.loadConfig(this)
        renderedConfig.value = config
        params.flags = baseFlags(config)
        params.alpha = config.systemWindowAlpha()
        params.gravity = Gravity.TOP or config.alignment.toGravity()
        if (config.alignment == OverlayAlignment.CENTER) params.x = 0
        view.elevation = 0f
        minimalLockScreenApplied = null
        applyContentLayout(
            minimalLockScreen = keyguardManager.isKeyguardLocked &&
                    config.lockScreenMode == LockScreenOverlayMode.MINIMAL,
        )
        constrainPosition(view, params)
        runCatching { windowManager.updateViewLayout(view, params) }
        view.post {
            constrainPosition(view, params)
            runCatching { windowManager.updateViewLayout(view, params) }
        }
    }

    private fun baseFlags(value: OverlayConfig): Int {
        var flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
        if (value.clickThrough) flags = flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        return flags
    }

    private fun render(state: OverlayLyricsState) {
        val view = overlayView ?: return
        when (config.visibility(state.isPlaying, keyguardManager.isKeyguardLocked)) {
            OverlayVisibility.HIDDEN_ON_LOCK_SCREEN -> {
                view.visibility = View.GONE
                LyricsOverlayController.report(OverlayRuntimeStatus.HiddenOnLockScreen)
                return
            }

            OverlayVisibility.HIDDEN_WHILE_PAUSED -> {
                view.visibility = View.GONE
                LyricsOverlayController.report(OverlayRuntimeStatus.HiddenWhilePaused)
                return
            }

            OverlayVisibility.MINIMAL_ON_LOCK_SCREEN -> {
                view.visibility = View.VISIBLE
                applyContentLayout(minimalLockScreen = true)
            }

            OverlayVisibility.VISIBLE -> {
                view.visibility = View.VISIBLE
                applyContentLayout(minimalLockScreen = false)
            }
        }
        renderedState.value = state
        LyricsOverlayController.report(OverlayRuntimeStatus.Visible)
    }

    private fun applyContentLayout(minimalLockScreen: Boolean) {
        if (minimalLockScreenApplied == minimalLockScreen) return
        val view = overlayView ?: return
        val params = layoutParams ?: return
        minimalLockScreenApplied = minimalLockScreen
        renderedMinimalMode.value = minimalLockScreen
        params.width = if (minimalLockScreen) {
            overlayWidthPx().coerceAtMost(dp(MAX_LOCK_SCREEN_MINIMAL_WIDTH_DP))
        } else {
            overlayWidthPx()
        }
        params.height = if (minimalLockScreen) {
            dp(LOCK_SCREEN_MINIMAL_HEIGHT_DP)
        } else {
            WindowManager.LayoutParams.WRAP_CONTENT
        }
        constrainPosition(view, params)
        runCatching { windowManager.updateViewLayout(view, params) }
    }

    private fun persistPosition(params: WindowManager.LayoutParams) {
        getSharedPreferences(POSITION_PREFS, MODE_PRIVATE)
            .edit()
            .putInt(KEY_X, params.x)
            .putInt(KEY_Y, params.y)
            .apply()
    }

    private fun registerKeyguardReceiver() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        ContextCompat.registerReceiver(
            this,
            keyguardReceiver,
            filter,
            ContextCompat.RECEIVER_EXPORTED,
        )
    }

    private fun overlayWidthPx(): Int {
        val (availableWidth, _) = availableArea()
        val margin = dp(WINDOW_MARGIN_DP)
        return (availableWidth - margin * 2)
            .coerceAtLeast(1)
            .coerceAtMost(dp(MAX_OVERLAY_WIDTH_DP))
    }

    private fun availableArea(): Pair<Int, Int> {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val metrics = windowManager.currentWindowMetrics
            val insets = metrics.windowInsets.getInsetsIgnoringVisibility(
                WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout(),
            )
            return (metrics.bounds.width() - insets.left - insets.right).coerceAtLeast(1) to
                    (metrics.bounds.height() - insets.top - insets.bottom).coerceAtLeast(1)
        }
        @Suppress("DEPRECATION")
        return resources.displayMetrics.widthPixels.coerceAtLeast(1) to
                resources.displayMetrics.heightPixels.coerceAtLeast(1)
    }

    private fun constrainPosition(view: View, params: WindowManager.LayoutParams) {
        val (availableWidth, availableHeight) = availableArea()
        val fallbackHeight = when {
            renderedMinimalMode.value -> dp(LOCK_SCREEN_MINIMAL_HEIGHT_DP)
            config.mode == OverlayMode.EXPANDED -> dp(MAX_EXPANDED_HEIGHT_DP)
            else -> dp(112)
        }
        val position = clampOverlayPosition(
            x = params.x,
            y = params.y,
            alignment = config.alignment,
            availableWidth = availableWidth,
            availableHeight = availableHeight,
            overlayWidth = params.width.coerceAtLeast(1),
            overlayHeight = view.height.takeIf { it > 0 } ?: fallbackHeight,
            margin = dp(WINDOW_MARGIN_DP),
        )
        params.x = position.x
        params.y = position.y
    }

    private fun OverlayAlignment.toGravity(): Int = when (this) {
        OverlayAlignment.START -> Gravity.START
        OverlayAlignment.CENTER -> Gravity.CENTER_HORIZONTAL
        OverlayAlignment.END -> Gravity.END
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).roundToInt()

    private inner class DragTouchListener : View.OnTouchListener {
        private var initialX = 0
        private var initialY = 0
        private var initialTouchX = 0f
        private var initialTouchY = 0f

        override fun onTouch(view: View, event: MotionEvent): Boolean {
            if (config.locked || config.clickThrough) return false
            val params = layoutParams ?: return false
            return when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    if (config.alignment != OverlayAlignment.CENTER) {
                        params.x = initialX + (event.rawX - initialTouchX).roundToInt()
                    }
                    params.y = initialY + (event.rawY - initialTouchY).roundToInt()
                    constrainPosition(view, params)
                    runCatching { windowManager.updateViewLayout(view, params) }
                    true
                }

                MotionEvent.ACTION_UP -> {
                    persistPosition(params)
                    view.performClick()
                    true
                }

                MotionEvent.ACTION_CANCEL -> {
                    persistPosition(params)
                    true
                }

                else -> false
            }
        }
    }

    private class OverlayViewTreeOwners : LifecycleOwner, SavedStateRegistryOwner {
        private val lifecycleRegistry = LifecycleRegistry(this)
        private val savedStateController = SavedStateRegistryController.create(this)

        override val lifecycle: Lifecycle
            get() = lifecycleRegistry
        override val savedStateRegistry: SavedStateRegistry
            get() = savedStateController.savedStateRegistry

        fun performRestore(state: Bundle?) {
            savedStateController.performAttach()
            savedStateController.performRestore(state)
        }

        fun resume() {
            lifecycleRegistry.currentState = Lifecycle.State.RESUMED
        }

        fun destroy() {
            lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        }
    }

    companion object {
        const val ACTION_START = "xyz.tosdev.yetasimplelyicsapp.overlay.START"
        const val ACTION_STOP = "xyz.tosdev.yetasimplelyicsapp.overlay.STOP"
        const val ACTION_RECONFIGURE = "xyz.tosdev.yetasimplelyicsapp.overlay.RECONFIGURE"

        private const val POSITION_PREFS = "lyrics_overlay_position"
        private const val KEY_X = "x"
        private const val KEY_Y = "y"
        private const val WINDOW_MARGIN_DP = 8
        private const val MAX_OVERLAY_WIDTH_DP = 520
        private const val MAX_EXPANDED_HEIGHT_DP = 280
        private const val MAX_LOCK_SCREEN_MINIMAL_WIDTH_DP = 360
        private const val LOCK_SCREEN_MINIMAL_HEIGHT_DP = 56
        private const val KEYGUARD_EVENT_SETTLE_DELAY_MS = 500L
    }
}

@Composable
private fun OverlayLyricsContent(
    state: OverlayLyricsState,
    config: OverlayConfig,
    minimalLockScreen: Boolean,
) {
    val textAlign = when (config.alignment) {
        OverlayAlignment.START -> TextAlign.Start
        OverlayAlignment.END -> TextAlign.End
        else -> TextAlign.Center
    }

    val boxAlignment = when (config.alignment) {
        OverlayAlignment.START -> Alignment.CenterStart
        OverlayAlignment.END -> Alignment.CenterEnd
        else -> Alignment.Center
    }

    val contentOpacity = config.renderedContentOpacity()
    val (baseTextColor, baseAccentColor) = when (config.preset) {
        OverlayPreset.HIGH_CONTRAST -> Color.White to Color.Yellow
        OverlayPreset.APPLE_INSPIRED -> Color.White to Color(0xFFA8C7FA)
        OverlayPreset.DUET -> Color.White to Color(0xFFFFA8D8)
        else -> Color.White to MaterialTheme.colorScheme.primary
    }
    val textColor = baseTextColor.copy(alpha = baseTextColor.alpha * contentOpacity)
    val accentColor = baseAccentColor.copy(alpha = baseAccentColor.alpha * contentOpacity)
    val resolvedKaraokeColors = rememberKaraokeColors(
        style = state.karaokeStyle,
        artworkUri = state.artworkUri,
        motionLevel = state.motionLevel,
    )
    val overlayKaraokeColors = remember(resolvedKaraokeColors, state.karaokeStyle) {
        resolvedKaraokeColors.forTransparentOverlay(state.karaokeStyle)
    }

    val effectiveBackground = config.effectiveBackground()
    val bg = when (effectiveBackground) {
        OverlayBackground.TEXT_ONLY, OverlayBackground.TRANSPARENT -> Color.Transparent
        OverlayBackground.FROSTED -> Color.White.copy(alpha = 0.13f)
        OverlayBackground.ARTWORK -> Color(0xFF43325E).copy(alpha = 0.82f)
        OverlayBackground.PILL -> Color.Black.copy(alpha = 0.72f)
        OverlayBackground.CARD -> MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)
        else -> Color.Black.copy(alpha = 0.64f)
    }

    val density = LocalDensity.current
    val textDensity = Density(
        density = density.density,
        fontScale = density.fontScale * config.textScale,
    )
    CompositionLocalProvider(LocalDensity provides textDensity) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = boxAlignment
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(if (minimalLockScreen) 1f else 0.95f),
                shape = RoundedCornerShape(config.cornerRadius.dp),
                color = bg.copy(alpha = bg.alpha * contentOpacity),
                border = if (
                    config.layout != OverlayLayout.CURRENT_WORD &&
                    (config.showBorder || effectiveBackground == OverlayBackground.FROSTED)
                ) {
                    BorderStroke(1.dp, Color.White.copy(alpha = 0.22f * contentOpacity))
                } else {
                    null
                },
                shadowElevation = 0.dp,
            ) {
                Column(
                    modifier = Modifier.padding(
                        if (
                            effectiveBackground == OverlayBackground.TEXT_ONLY ||
                            effectiveBackground == OverlayBackground.TRANSPARENT
                        ) {
                            0.dp
                        } else {
                            config.paddingDp.dp
                        }
                    ),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val layout = if (minimalLockScreen) {
                        OverlayLayout.CURRENT_LINE
                    } else {
                        config.layout
                    }
                    if (layout == OverlayLayout.DESKTOP_LYRICS) {
                        Text(
                            text = (
                                state.trackTitle ?: AppLanguageManager.localizedContext(LocalContext.current)
                                    .getString(R.string.system_overlay_untitled)
                                ).uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = accentColor,
                            textAlign = textAlign
                        )
                    }

                    when (layout) {
                        OverlayLayout.CURRENT_WORD -> CurrentWordLyrics(
                            activeLine = state.currentCue,
                            activeWordId = state.activeWordId,
                            timeline = state.timeline,
                            fallbackToLine = true,
                            motionLevel = state.motionLevel,
                            visualStyle = state.karaokeStyle,
                            artworkUri = state.artworkUri,
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = MaterialTheme.typography.displaySmall,
                            contentAlpha = contentOpacity,
                            colors = overlayKaraokeColors,
                        )

                        OverlayLayout.PREVIOUS_CURRENT_NEXT -> {
                            OverlayContextLine(
                                state.previousCue,
                                state.previousLine,
                                state,
                                textColor,
                                textAlign,
                                contentOpacity,
                                overlayKaraokeColors,
                            )
                            OverlayCurrentLine(
                                state,
                                textColor,
                                textAlign,
                                minimalLockScreen = false,
                                contentOpacity = contentOpacity,
                                karaokeColors = overlayKaraokeColors,
                            )
                            OverlayContextLine(
                                state.nextCue,
                                state.nextLine,
                                state,
                                textColor,
                                textAlign,
                                contentOpacity,
                                overlayKaraokeColors,
                            )
                        }

                        OverlayLayout.CURRENT_AND_NEXT,
                        OverlayLayout.DESKTOP_LYRICS,
                            -> {
                            OverlayCurrentLine(
                                state,
                                textColor,
                                textAlign,
                                minimalLockScreen = false,
                                contentOpacity = contentOpacity,
                                karaokeColors = overlayKaraokeColors,
                            )
                            OverlayContextLine(
                                state.nextCue,
                                state.nextLine,
                                state,
                                textColor,
                                textAlign,
                                contentOpacity,
                                overlayKaraokeColors,
                            )
                        }

                        OverlayLayout.KARAOKE -> {
                            val duetCues = state.activeCues.takeIf {
                                config.preset == OverlayPreset.DUET && it.size > 1
                            }
                            if (duetCues != null) {
                                duetCues.take(2).forEach { cue ->
                                    KaraokeText(
                                        line = cue,
                                        positionMs = { state.timeline?.positionMs },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 3.dp),
                                        textStyle = MaterialTheme.typography.headlineMedium,
                                        visualStyle = state.karaokeStyle,
                                        alignment = cue.alignment,
                                        motionLevel = state.motionLevel,
                                        artworkUri = state.artworkUri,
                                        activeForegroundAlpha = contentOpacity,
                                        inactiveTextAlpha = contentOpacity,
                                        colors = overlayKaraokeColors,
                                    )
                                }
                            } else {
                                OverlayCurrentLine(
                                    state,
                                    textColor,
                                    textAlign,
                                    minimalLockScreen = false,
                                    large = true,
                                    contentOpacity = contentOpacity,
                                    karaokeColors = overlayKaraokeColors,
                                )
                            }
                        }

                        OverlayLayout.CURRENT_LINE -> OverlayCurrentLine(
                            state,
                            textColor,
                            textAlign,
                            minimalLockScreen,
                            contentOpacity = contentOpacity,
                            karaokeColors = overlayKaraokeColors,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OverlayCurrentLine(
    state: OverlayLyricsState,
    textColor: Color,
    textAlign: TextAlign,
    minimalLockScreen: Boolean,
    large: Boolean = false,
    contentOpacity: Float,
    karaokeColors: KaraokeColors,
) {
    AnimatedContent(
        targetState = state.currentCue,
        contentKey = { it?.id ?: state.currentLine.orEmpty() },
        transitionSpec = { overlayLineTransition(state.motionLevel) },
        label = "floating current lyric",
    ) { cue ->
        val style = when {
            minimalLockScreen -> MaterialTheme.typography.bodyMedium
            large -> MaterialTheme.typography.headlineMedium
            else -> MaterialTheme.typography.headlineSmall
        }
        if (cue != null) {
            KaraokeText(
                line = cue,
                positionMs = { state.timeline?.positionMs },
                modifier = Modifier.fillMaxWidth(),
                textStyle = style,
                visualStyle = state.karaokeStyle,
                alignment = cue.alignment,
                motionLevel = state.motionLevel,
                artworkUri = state.artworkUri,
                activeForegroundAlpha = contentOpacity,
                inactiveTextAlpha = contentOpacity,
                colors = karaokeColors,
            )
        } else {
            Text(
                text = if (minimalLockScreen) {
                    state.currentLine ?: state.trackTitle ?:
                    AppLanguageManager.localizedContext(LocalContext.current)
                        .getString(R.string.system_overlay_playing)
                } else {
                    state.currentLine ?: "..."
                },
                style = style,
                color = textColor,
                textAlign = textAlign,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun OverlayContextLine(
    cue: LyricLineUi?,
    fallback: String?,
    state: OverlayLyricsState,
    textColor: Color,
    textAlign: TextAlign,
    contentOpacity: Float,
    karaokeColors: KaraokeColors,
) {
    AnimatedContent(
        targetState = cue,
        contentKey = { it?.id ?: fallback.orEmpty() },
        transitionSpec = { overlayNextTransition(state.motionLevel) },
        label = "floating context lyric",
    ) { line ->
        if (line != null) {
            KaraokeText(
                line = line,
                positionMs = { null },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 5.dp),
                textStyle = MaterialTheme.typography.bodyMedium,
                visualStyle = state.karaokeStyle,
                alignment = line.alignment,
                motionLevel = state.motionLevel,
                artworkUri = state.artworkUri,
                isActive = false,
                activeForegroundAlpha = 0f,
                inactiveTextAlpha = 0.66f * contentOpacity,
                colors = karaokeColors,
            )
        } else if (!fallback.isNullOrBlank()) {
            Text(
                text = fallback,
                style = MaterialTheme.typography.bodyMedium,
                color = textColor.copy(alpha = textColor.alpha * 0.6f),
                textAlign = textAlign,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 5.dp),
            )
        }
    }
}

private fun overlayLineTransition(level: MotionLevel): ContentTransform = when (level) {
    MotionLevel.OFF -> EnterTransition.None togetherWith ExitTransition.None
    MotionLevel.REDUCED -> fadeIn(tween(130)) togetherWith fadeOut(tween(110))
    MotionLevel.NORMAL -> (
            fadeIn(tween(220)) + slideInVertically(tween(260)) { it / 3 }
            ) togetherWith (
            fadeOut(tween(170)) + slideOutVertically(tween(210)) { -it / 3 }
            )

    MotionLevel.ENHANCED -> (
            fadeIn(tween(260)) + slideInVertically(tween(330)) { it / 3 } +
                    scaleIn(initialScale = 0.97f, animationSpec = tween(330))
            ) togetherWith (
            fadeOut(tween(190)) + slideOutVertically(tween(250)) { -it / 3 } +
                    scaleOut(targetScale = 0.97f, animationSpec = tween(250))
            )
}

private fun overlayNextTransition(level: MotionLevel): ContentTransform = when (level) {
    MotionLevel.OFF -> EnterTransition.None togetherWith ExitTransition.None
    MotionLevel.REDUCED -> fadeIn(tween(120)) togetherWith fadeOut(tween(100))
    else -> (
            fadeIn(tween(200)) + slideInVertically(tween(250)) { it / 2 }
            ) togetherWith (
            fadeOut(tween(150)) + slideOutVertically(tween(220)) { -it / 2 }
            )
}
