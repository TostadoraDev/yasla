package xyz.tosdev.yetasimplelyicsapp

import android.os.Bundle
import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import android.os.Build
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import xyz.tosdev.yetasimplelyicsapp.domain.lyrics.LyricsSource
import xyz.tosdev.yetasimplelyicsapp.domain.lyrics.parser.LyricsParseContext
import xyz.tosdev.yetasimplelyicsapp.domain.lyrics.parser.TtmlLyricsParser
import xyz.tosdev.yetasimplelyicsapp.ui.*
import xyz.tosdev.yetasimplelyicsapp.ui.theme.LyricsTheme
import xyz.tosdev.yetasimplelyicsapp.widget.LyricsWidgetConfig
import xyz.tosdev.yetasimplelyicsapp.widget.WidgetConfigurationScreen
import xyz.tosdev.yetasimplelyicsapp.widget.LyricsWidgetSnapshot
import xyz.tosdev.yetasimplelyicsapp.widget.WidgetPresentationState
import xyz.tosdev.yetasimplelyicsapp.widget.WidgetLyricsSyncType

/** Local demo data and actions; this activity never touches MediaSession or stored settings. */
class RefinementPreviewActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        // Tests select the interface locale without changing the user's saved preference.
        val config = Configuration(newBase.resources.configuration)
        config.setLocales(LocaleList.forLanguageTags(testLanguageTag))
        super.attachBaseContext(newBase.createConfigurationContext(config))
    }
    companion object { var testLanguageTag = "es" }
    var widgetSnapshot by mutableStateOf(LyricsWidgetSnapshot(trackKey = "demo-track", title = "Demo track",
        activeLineId = "demo-line", activeLine = "QUIERE estar aquí", activeWordId = "demo-word",
        activeWord = "QUIERE", hasReliableWordSync = true, syncType = WidgetLyricsSyncType.WORD,
        lyricsLanguageTag = "es", presentationState = WidgetPresentationState.LYRICS_AVAILABLE))

    private val timeline = LyricsTimeline(5_000)
    var seekCount = 0
        private set
    var lastSeek = 0f
        private set
    var preview by mutableStateOf(NowPlayingUiState())
        private set
    var savedWidgetConfig: LyricsWidgetConfig? = null
        private set
    val initialWidgetConfig = LyricsWidgetConfig(showTranslation = true, showPlaybackControls = true,
        textScale = 1.2f, cornerRadius = 28, verticalBias = 0.6f, primaryColorHex = "#63A7FF")

    fun reportPlaybackPosition(time: Long) {
        timeline.positionMs = time
        preview = preview.copy(positionMs = time)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        val input = assets.open("amll-motion-fixture.ttml").bufferedReader().use { it.readText() }
        val lines = TtmlLyricsParser().parse(input,
            LyricsParseContext("refinement-demo", "refinement-demo", LyricsSource("test")))
            .document!!.presentationLines()
        preview = NowPlayingUiState(
            title = "Motion study", artist = "YASLA · Demo data", album = "Native lyrics",
            positionMs = 5_000, durationMs = 36_000, hasActiveSession = true,
            canSeek = true, canTogglePlayback = true, canSkipNext = true, canSkipPrevious = true,
            lyrics = lines, lyricsLoadState = LyricsLoadState.AVAILABLE,
            activeLineId = lines.firstOrNull { (it.startTimeMs ?: 0) <= 5_000 &&
                (it.endTimeMs ?: 0) > 5_000 }?.id, timeline = timeline, isPreview = true,
        )
        setContent {
            if (intent.getStringExtra("screen") == "widget") {
                LyricsTheme(darkTheme = intent.getBooleanExtra("dark", true)) {
                    WidgetConfigurationScreen(initialConfig = initialWidgetConfig,
                        onSave = { savedWidgetConfig = it }, snapshot = widgetSnapshot)
                }
                return@setContent
            }
            AdvancedLyricsApp(
                state = LyricsAppUiState(nowPlaying = preview,
                    settings = SettingsUiState(themeMode = AppThemeMode.DARK, dynamicColor = false)),
                actions = LyricsAppActions(
                    onOffsetChange = { preview = preview.copy(offsetMs = it) },
                    onSeek = {
                        seekCount++
                        lastSeek = it
                        val time = (it * preview.durationMs).toLong()
                        timeline.positionMs = time
                        preview = preview.copy(positionMs = time,
                            activeLineId = lines.lastOrNull { (it.startTimeMs ?: Long.MAX_VALUE) <= time }?.id)
                    },
                    onTogglePlayback = { preview = preview.copy(isPlaying = !preview.isPlaying) },
                ),
            )
        }
    }
}
