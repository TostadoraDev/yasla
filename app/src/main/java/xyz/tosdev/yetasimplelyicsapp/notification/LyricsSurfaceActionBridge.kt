package xyz.tosdev.yetasimplelyicsapp.notification

enum class LyricsSurfaceCommand {
    TOGGLE_OVERLAY,
    TOGGLE_OVERLAY_LOCK,
    OFFSET_BACK_500_MS,
    OFFSET_FORWARD_500_MS,
}

fun interface LyricsSurfaceActionHandler {
    fun onCommand(command: LyricsSurfaceCommand)
}

object LyricsSurfaceActionBridge {
    @Volatile
    private var handler: LyricsSurfaceActionHandler? = null

    fun install(value: LyricsSurfaceActionHandler) {
        handler = value
    }

    fun uninstall(value: LyricsSurfaceActionHandler) {
        if (handler === value) handler = null
    }

    fun dispatch(command: LyricsSurfaceCommand): Boolean {
        val current = handler ?: return false
        current.onCommand(command)
        return true
    }
}
