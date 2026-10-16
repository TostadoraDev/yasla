package xyz.tosdev.yetasimplelyicsapp.overlay

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import xyz.tosdev.yetasimplelyicsapp.ui.CurrentWordAlignment

object LyricsOverlayController {
    private const val PREFS = "lyrics_overlay"
    private const val KEY_MODE = "mode"
    private const val KEY_LAYOUT = "layout"
    private const val KEY_CURRENT_WORD_ALIGNMENT = "current_word_alignment"
    private const val KEY_PRESET = "preset"
    private const val KEY_BACKGROUND = "background"
    private const val KEY_BACKGROUND_DEFAULT_MIGRATED = "background_default_migrated_v2"
    private const val KEY_ALIGNMENT = "alignment"
    private const val KEY_LOCKED = "locked"
    private const val KEY_CLICK_THROUGH = "click_through"
    private const val KEY_HIDE_WHEN_PAUSED = "hide_when_paused"
    private const val KEY_SHOW_ON_LOCK_SCREEN = "show_on_lock_screen"
    private const val KEY_LOCK_SCREEN_MODE = "lock_screen_mode"
    private const val KEY_LOCK_SCREEN_NOTIFICATION = "lock_screen_notification"
    private const val KEY_OPACITY = "opacity"
    private const val KEY_TEXT_SCALE = "text_scale"
    private const val KEY_CORNER_RADIUS = "corner_radius"
    private const val KEY_PADDING = "padding"
    private const val KEY_SHOW_BORDER = "show_border"
    private const val KEY_SOFT_SHADOW = "soft_shadow"
    private const val KEY_ENABLED = "enabled_persistence"

    private val mutableContent = MutableStateFlow(OverlayLyricsState())
    val content: StateFlow<OverlayLyricsState> = mutableContent.asStateFlow()

    private val mutableStatus = MutableStateFlow<OverlayRuntimeStatus>(OverlayRuntimeStatus.Stopped)
    val status: StateFlow<OverlayRuntimeStatus> = mutableStatus.asStateFlow()

    fun canDrawOverlays(context: Context): Boolean = Settings.canDrawOverlays(context)

    fun permissionIntent(context: Context): Intent = Intent(
        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
        Uri.parse("package:${context.packageName}"),
    )

    fun start(
        context: Context,
        config: OverlayConfig = loadConfig(context),
    ): OverlayRuntimeStatus {
        if (!canDrawOverlays(context)) {
            mutableStatus.value = OverlayRuntimeStatus.PermissionRequired
            return mutableStatus.value
        }
        saveConfig(context, config)
        setPersistentEnabled(context, true)
        mutableStatus.value = OverlayRuntimeStatus.Starting
        val intent = Intent(context, LyricsOverlayService::class.java)
            .setAction(LyricsOverlayService.ACTION_START)
        return startForegroundServiceSafely(context, intent)
    }

    fun stop(context: Context) {
        setPersistentEnabled(context, false)
        context.stopService(Intent(context, LyricsOverlayService::class.java))
    }

    fun updateConfig(context: Context, config: OverlayConfig) {
        saveConfig(context, config)
        if (!isPersistentEnabled(context)) return

        startForegroundServiceSafely(
            context,
            Intent(context, LyricsOverlayService::class.java)
                .setAction(LyricsOverlayService.ACTION_RECONFIGURE),
        )
    }

    fun refreshLocalization(context: Context) {
        if (!isPersistentEnabled(context)) return
        startForegroundServiceSafely(
            context,
            Intent(context, LyricsOverlayService::class.java)
                .setAction(LyricsOverlayService.ACTION_RECONFIGURE),
        )
    }

    fun publish(state: OverlayLyricsState) {
        mutableContent.value = state.normalized()
    }

    private fun startForegroundServiceSafely(
        context: Context,
        intent: Intent,
    ): OverlayRuntimeStatus {
        return try {
            ContextCompat.startForegroundService(context, intent)
            mutableStatus.value
        } catch (error: RuntimeException) {
            mutableStatus.value = OverlayRuntimeStatus.Error(
                error.message ?: error.javaClass.simpleName,
            )
            mutableStatus.value
        }
    }

    internal fun report(status: OverlayRuntimeStatus) {
        mutableStatus.value = status
    }

    fun loadConfig(context: Context): OverlayConfig {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val lockScreenMode = prefs.getString(KEY_LOCK_SCREEN_MODE, null)
            ?.let { runCatching { LockScreenOverlayMode.valueOf(it) }.getOrNull() }
            ?: if (prefs.getBoolean(KEY_SHOW_ON_LOCK_SCREEN, false)) {
                LockScreenOverlayMode.ALWAYS
            } else {
                LockScreenOverlayMode.OFF
            }
        val storedBackground = prefs.getString(KEY_BACKGROUND, null)
            ?.let { runCatching { OverlayBackground.valueOf(it) }.getOrNull() }
        val backgroundMigrationComplete = prefs.getBoolean(
            KEY_BACKGROUND_DEFAULT_MIGRATED,
            false,
        )
        val background = migrateLegacyOverlayBackground(
            storedBackground = storedBackground,
            migrationComplete = backgroundMigrationComplete,
        )
        if (!backgroundMigrationComplete) {
            prefs.edit()
                .putString(KEY_BACKGROUND, background.name)
                .putBoolean(KEY_BACKGROUND_DEFAULT_MIGRATED, true)
                .apply()
        }
        return OverlayConfig(
            mode = prefs.getString(KEY_MODE, null)
                ?.let { runCatching { OverlayMode.valueOf(it) }.getOrNull() }
                ?: OverlayMode.COMPACT,
            layout = prefs.getString(KEY_LAYOUT, null)
                ?.let { runCatching { OverlayLayout.valueOf(it) }.getOrNull() }
                ?: OverlayLayout.CURRENT_LINE,
            currentWordAlignment = prefs.getString(KEY_CURRENT_WORD_ALIGNMENT, null)
                ?.let { runCatching { CurrentWordAlignment.valueOf(it) }.getOrNull() }
                ?: CurrentWordAlignment.CENTER,
            preset = prefs.getString(KEY_PRESET, null)
                ?.let { runCatching { OverlayPreset.valueOf(it) }.getOrNull() }
                ?: OverlayPreset.MINIMAL,
            background = background,
            alignment = prefs.getString(KEY_ALIGNMENT, null)
                ?.let { runCatching { OverlayAlignment.valueOf(it) }.getOrNull() }
                ?: OverlayAlignment.CENTER,
            locked = prefs.getBoolean(KEY_LOCKED, false),
            clickThrough = prefs.getBoolean(KEY_CLICK_THROUGH, false),
            hideWhenPaused = prefs.getBoolean(KEY_HIDE_WHEN_PAUSED, true),
            lockScreenMode = lockScreenMode,
            useLockScreenNotification = prefs.getBoolean(KEY_LOCK_SCREEN_NOTIFICATION, false),
            opacity = prefs.getFloat(KEY_OPACITY, 0.92f),
            textScale = prefs.getFloat(KEY_TEXT_SCALE, 1f),
            cornerRadius = prefs.getFloat(KEY_CORNER_RADIUS, 20f),
            paddingDp = prefs.getFloat(KEY_PADDING, 16f),
            showBorder = prefs.getBoolean(KEY_SHOW_BORDER, false),
            softShadow = prefs.getBoolean(KEY_SOFT_SHADOW, true),
        ).normalized()
    }

    fun isPersistentEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ENABLED, false)

    private fun setPersistentEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ENABLED, enabled)
            .apply()
    }

    private fun saveConfig(context: Context, value: OverlayConfig) {
        val config = value.normalized()
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_MODE, config.mode.name)
            .putString(KEY_LAYOUT, config.layout.name)
            .putString(KEY_CURRENT_WORD_ALIGNMENT, config.currentWordAlignment.name)
            .putString(KEY_PRESET, config.preset.name)
            .putString(KEY_BACKGROUND, config.background.name)
            .putBoolean(KEY_BACKGROUND_DEFAULT_MIGRATED, true)
            .putString(KEY_ALIGNMENT, config.alignment.name)
            .putBoolean(KEY_LOCKED, config.locked)
            .putBoolean(KEY_CLICK_THROUGH, config.clickThrough)
            .putBoolean(KEY_HIDE_WHEN_PAUSED, config.hideWhenPaused)
            .putString(KEY_LOCK_SCREEN_MODE, config.lockScreenMode.name)
            .putBoolean(KEY_LOCK_SCREEN_NOTIFICATION, config.useLockScreenNotification)
            .remove(KEY_SHOW_ON_LOCK_SCREEN)
            .putFloat(KEY_OPACITY, config.opacity)
            .putFloat(KEY_TEXT_SCALE, config.textScale)
            .putFloat(KEY_CORNER_RADIUS, config.cornerRadius)
            .putFloat(KEY_PADDING, config.paddingDp)
            .putBoolean(KEY_SHOW_BORDER, config.showBorder)
            .putBoolean(KEY_SOFT_SHADOW, config.softShadow)
            .apply()
    }
}
