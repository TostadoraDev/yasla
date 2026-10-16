package xyz.tosdev.yetasimplelyicsapp.widget

import android.content.Context
import android.content.pm.ApplicationInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal data class WidgetDeliveryTrace(
    val appWidgetId: Int,
    val wordIdHash: Int,
    val sampleAtElapsedMs: Long,
    val submittedAtElapsedMs: Long,
    val sourceTimestampMs: Long = -1L,
    val scheduledAtElapsedMs: Long = -1L,
    val requestedAtElapsedMs: Long = -1L,
    val preparationStartedAtElapsedMs: Long = -1L,
    val preparationDurationMs: Long = -1L,
    val obsoleteCount: Long = 0L,
)

/** Last Binder submission only. This is not an acknowledgement from the launcher. */
internal object WidgetDeliveryDiagnostics {
    private val latest = MutableStateFlow<WidgetDeliveryTrace?>(null)
    private val obsolete = MutableStateFlow(0L)
    val lastSubmission = latest.asStateFlow()
    val obsoleteSubmissions = obsolete.asStateFlow()

    fun record(context: Context, trace: WidgetDeliveryTrace) {
        if (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) latest.value = trace
    }

    fun recordObsolete(context: Context, count: Long) {
        if (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            obsolete.value = count
        }
    }
}
