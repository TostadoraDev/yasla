package xyz.tosdev.yetasimplelyicsapp

import android.app.Application
import android.content.Context
import xyz.tosdev.yetasimplelyicsapp.localization.AppLanguageManager

class LyricsApplication : Application() {
    val container by lazy { LyricsAppContainer(this) }

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(AppLanguageManager.wrap(base))
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        container.refreshLocalizedSurfaces()
    }

    override fun onCreate() {
        super.onCreate()
        AppLanguageManager.initialize(this)
        container.start()
    }
}
