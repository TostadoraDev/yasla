package xyz.tosdev.yetasimplelyicsapp.overlay

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class LyricsBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            if (LyricsOverlayController.isPersistentEnabled(context)) {
                LyricsOverlayController.start(context)
            }
        }
    }
}
