package xyz.tosdev.yetasimplelyicsapp

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import xyz.tosdev.yetasimplelyicsapp.ui.AdvancedLyricsApp
import xyz.tosdev.yetasimplelyicsapp.ui.AppThemeMode
import xyz.tosdev.yetasimplelyicsapp.ui.LyricsAppActions
import xyz.tosdev.yetasimplelyicsapp.ui.LyricsAppUiState
import xyz.tosdev.yetasimplelyicsapp.ui.SettingsUiState

class KeepScreenOnTestActivity : ComponentActivity() {
    var settings by mutableStateOf(
        SettingsUiState(
            themeMode = AppThemeMode.DARK,
            dynamicColor = false,
            keepScreenAwake = true,
        ),
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AdvancedLyricsApp(
                state = LyricsAppUiState(settings = settings),
                actions = LyricsAppActions(onSettingsChange = { settings = it }),
                onKeepScreenOnChanged = { enabled ->
                    if (enabled) {
                        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    } else {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    }
                },
            )
        }
    }

    override fun onDestroy() {
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        super.onDestroy()
    }
}
