package xyz.tosdev.yetasimplelyicsapp

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Bundle
import android.util.SizeF
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.RemoteViews
import androidx.activity.ComponentActivity
import xyz.tosdev.yetasimplelyicsapp.widget.LyricsWidgetReceiver

/** Isolated host for instrumented, real RemoteViews captures. Never modifies launcher instances. */
class Widgets2HostActivity : ComponentActivity() {
    private lateinit var frame: FrameLayout
    var widgetView: View? = null
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (android.os.Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        frame = FrameLayout(this)
        frame.addView(object : View(this) {
            val paint = Paint()
            override fun onDraw(canvas: Canvas) {
                val unit = 24f * resources.displayMetrics.density
                for (y in 0..(height / unit).toInt()) for (x in 0..(width / unit).toInt()) {
                    paint.color = if ((x + y) % 2 == 0) 0xff292c31.toInt() else 0xff41454c.toInt()
                    canvas.drawRect(x * unit, y * unit, (x + 1) * unit, (y + 1) * unit, paint)
                }
            }
        }, FrameLayout.LayoutParams(-1, -1))
        setContentView(frame)
    }

    fun showRemoteViews(remoteViews: RemoteViews, widthDp: Float, heightDp: Float) {
        val view = remoteViews.apply(this, frame)
        showWidgetView(view, widthDp, heightDp)
    }

    fun showWidgetView(view: View, widthDp: Float, heightDp: Float) {
        widgetView?.let(frame::removeView)
        widgetView = view
        val density = resources.displayMetrics.density
        frame.addView(view, FrameLayout.LayoutParams((widthDp * density).toInt(),
            (heightDp * density).toInt(), Gravity.CENTER))
    }
}
