package xyz.tosdev.yetasimplelyicsapp.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import xyz.tosdev.yetasimplelyicsapp.R
import xyz.tosdev.yetasimplelyicsapp.localization.AppLanguageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import xyz.tosdev.yetasimplelyicsapp.LyricsApplication
import xyz.tosdev.yetasimplelyicsapp.ui.AppThemeMode
import xyz.tosdev.yetasimplelyicsapp.ui.designsystem.rememberArtworkPalette
import xyz.tosdev.yetasimplelyicsapp.ui.theme.LyricsTheme

class WidgetConfigurationActivity : ComponentActivity() {
    private val repository by lazy {
        PreferencesLyricsWidgetConfigurationRepository(applicationContext)
    }

    private var pickedImagePath by mutableStateOf<String?>(null)
    private var originalImagePath: String? = null
    private var saved = false
    private val imageStore by lazy { WidgetIdleImageStore(this) }
    private val imagePicker = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) lifecycleScope.launch {
            val path = runCatching { imageStore.importFromPicker(uri, "widget_$appWidgetId") }.getOrNull()
            if (path == null) Toast.makeText(this@WidgetConfigurationActivity,
                getString(R.string.widget_image_import_failed), Toast.LENGTH_SHORT).show()
            else {
                if (pickedImagePath != originalImagePath) imageStore.delete(pickedImagePath)
                pickedImagePath = path
            }
        }
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLanguageManager.wrap(newBase))
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("picked_image", pickedImagePath)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        if (isFinishing && !saved && pickedImagePath != originalImagePath) imageStore.delete(pickedImagePath)
        super.onDestroy()
    }

    private var appWidgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pickedImagePath = savedInstanceState?.getString("picked_image")
        setResult(RESULT_CANCELED)

        appWidgetId = intent?.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        enableEdgeToEdge()
        lifecycleScope.launch {
            val loadedConfig = runCatching { repository.load(appWidgetId) }
                .getOrDefault(LyricsWidgetConfig())
            originalImagePath = loadedConfig.idleImagePath
            setContent {
                val appState by (application as LyricsApplication).container.state.collectAsStateWithLifecycle()
                val snapshot by (application as LyricsApplication).container.widgetSnapshot.collectAsStateWithLifecycle()
                val settings = appState.settings
                val dark = when (settings.themeMode) {
                    AppThemeMode.SYSTEM -> isSystemInDarkTheme()
                    AppThemeMode.DARK -> true
                    AppThemeMode.LIGHT -> false
                }
                val palette = rememberArtworkPalette(appState.nowPlaying.artworkUri, dark,
                    settings.highContrastLyrics, settings.motionLevel)
                LyricsTheme(darkTheme = dark, useDynamicColor = settings.dynamicColor,
                    palette = palette.takeUnless { settings.dynamicColor }) {
                    WidgetConfigurationScreen(
                        initialConfig = loadedConfig,
                        onSave = ::saveAndFinish,
                        motionLevel = settings.motionLevel,
                        onBack = ::finish,
                        snapshot = snapshot,
                        pickedImagePath = pickedImagePath,
                        onChooseImage = {
                            runCatching { imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
                                .onFailure { Toast.makeText(this@WidgetConfigurationActivity, getString(R.string.widget_image_picker_unavailable), Toast.LENGTH_SHORT).show() }
                        },
                    )
                }
            }
        }
    }

    private fun saveAndFinish(config: LyricsWidgetConfig) {
        lifecycleScope.launch {
            runCatching { repository.save(appWidgetId, config) }
                .onSuccess {
                    saved = true
                    if (originalImagePath != config.idleImagePath) imageStore.delete(originalImagePath)
                    if (pickedImagePath != config.idleImagePath) imageStore.delete(pickedImagePath)
                    setResult(
                        RESULT_OK,
                        Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId),
                    )
                    finish()
                }
                .onFailure {
                    Toast.makeText(
                        this@WidgetConfigurationActivity,
                        getString(R.string.studio_could_not_save_widget),
                        Toast.LENGTH_SHORT,
                    ).show()
                }
        }
    }
}

@Composable
internal fun LyricsWidgetContentMode.displayName(): String = when (this) {
    LyricsWidgetContentMode.AUTO -> stringResource(R.string.studio_auto)
    LyricsWidgetContentMode.CURRENT_WORD -> stringResource(R.string.studio_current_word)
    LyricsWidgetContentMode.CURRENT_LINE -> stringResource(R.string.studio_current_line)
    LyricsWidgetContentMode.CURRENT_NEXT -> stringResource(R.string.studio_current_next)
    LyricsWidgetContentMode.PREVIOUS_CURRENT_NEXT -> stringResource(R.string.studio_previous_current_next)
    LyricsWidgetContentMode.KARAOKE -> stringResource(R.string.studio_karaoke)
    LyricsWidgetContentMode.DESKTOP -> stringResource(R.string.studio_desktop_lyrics)
}

@Composable
internal fun LyricsWidgetContentMode.description(): String = when (this) {
    LyricsWidgetContentMode.AUTO ->
        stringResource(R.string.studio_adapt_words_lines_and_context_to_timing_and_available_space)
    LyricsWidgetContentMode.CURRENT_WORD -> stringResource(R.string.studio_show_complete_words_fall_back_to_the_line_without_word_sync)
    LyricsWidgetContentMode.CURRENT_LINE -> stringResource(R.string.studio_always_show_the_complete_synchronized_line)
    LyricsWidgetContentMode.CURRENT_NEXT -> stringResource(R.string.studio_show_current_and_next_lyrics)
    LyricsWidgetContentMode.PREVIOUS_CURRENT_NEXT -> stringResource(R.string.studio_full_context_around_the_current_line)
    LyricsWidgetContentMode.KARAOKE -> stringResource(R.string.studio_karaoke_preset_refresh_at_line_boundaries_within_widget_limits)
    LyricsWidgetContentMode.DESKTOP -> stringResource(R.string.studio_transparent_desktop_lyrics_presentation)
}

@Composable
internal fun LyricsWidgetLayout.displayName(): String = when (this) {
    LyricsWidgetLayout.MINIMAL -> stringResource(R.string.studio_minimal)
    LyricsWidgetLayout.TWO_LINES -> stringResource(R.string.studio_two_lines)
    LyricsWidgetLayout.TRANSPARENT -> stringResource(R.string.studio_transparent)
    LyricsWidgetLayout.KARAOKE_CARD -> stringResource(R.string.studio_karaoke_card)
    LyricsWidgetLayout.LARGE -> stringResource(R.string.studio_large)
    LyricsWidgetLayout.COMPACT_PLAYER -> stringResource(R.string.studio_compact_player)
    LyricsWidgetLayout.WORD_FOCUS -> stringResource(R.string.studio_word_focus)
}

@Composable
internal fun LyricsWidgetLayout.description(): String = when (this) {
    LyricsWidgetLayout.MINIMAL -> stringResource(R.string.studio_current_lyrics_with_minimal_decoration)
    LyricsWidgetLayout.TWO_LINES -> stringResource(R.string.studio_current_lyrics_with_a_translation_or_next_line)
    LyricsWidgetLayout.TRANSPARENT -> stringResource(R.string.studio_no_background_panel)
    LyricsWidgetLayout.KARAOKE_CARD -> stringResource(R.string.studio_large_card_with_optional_translation)
    LyricsWidgetLayout.LARGE -> stringResource(R.string.studio_metadata_with_previous_and_next_context)
    LyricsWidgetLayout.COMPACT_PLAYER -> stringResource(R.string.studio_metadata_and_controls_in_limited_space)
    LyricsWidgetLayout.WORD_FOCUS -> stringResource(R.string.studio_transparent_background_focused_on_content)
}
