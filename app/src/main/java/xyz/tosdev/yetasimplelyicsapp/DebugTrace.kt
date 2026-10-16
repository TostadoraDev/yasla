package xyz.tosdev.yetasimplelyicsapp

import android.content.Context
import android.content.pm.ApplicationInfo
import android.util.Log

internal inline fun debugTrace(
    context: Context,
    tag: String,
    message: () -> String,
) {
    if (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
        Log.d(tag, message())
    }
}
