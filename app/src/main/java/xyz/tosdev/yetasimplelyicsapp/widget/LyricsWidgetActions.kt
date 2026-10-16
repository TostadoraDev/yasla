package xyz.tosdev.yetasimplelyicsapp.widget

import android.content.Context
import android.content.Intent
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback

class WidgetPlaybackAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val command = parameters[commandKey]
            ?.let { wireValue -> WidgetPlaybackCommand.entries.firstOrNull { it.wireValue == wireValue } }
            ?: return

        context.sendBroadcast(
            Intent(ACTION_WIDGET_PLAYBACK_COMMAND)
                .setPackage(context.packageName)
                .putExtra(EXTRA_WIDGET_PLAYBACK_COMMAND, command.wireValue),
        )
    }

    companion object {
        const val ACTION_WIDGET_PLAYBACK_COMMAND =
            "xyz.tosdev.yetasimplelyicsapp.action.WIDGET_PLAYBACK_COMMAND"
        const val EXTRA_WIDGET_PLAYBACK_COMMAND = "widget_playback_command"

        val commandKey = ActionParameters.Key<String>("widget_playback_command_parameter")
    }
}
