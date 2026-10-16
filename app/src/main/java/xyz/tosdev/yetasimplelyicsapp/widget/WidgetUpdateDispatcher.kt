package xyz.tosdev.yetasimplelyicsapp.widget

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.CancellationException

/** One bounded queue. Re-sample the shared clock, never replay a backlog of lyric words. */
internal class WidgetUpdateDispatcher(
    private val currentSnapshot: () -> LyricsWidgetSnapshot,
    private val publish: suspend (LyricsWidgetSnapshot, isCurrent: () -> Boolean) -> Boolean,
    private val onFailure: (Exception) -> Unit = {},
    private val refresh: suspend (LyricsWidgetSnapshot, Set<Int>, isCurrent: () -> Boolean) -> Set<Int> =
        { _, _, _ -> emptySet() },
) {
    private val requests = Channel<Unit>(Channel.CONFLATED)
    private val refreshLock = Any()
    private val pendingRefreshes = linkedMapOf<Int, Long>()
    private var refreshVersion = 0L

    fun request() { requests.trySend(Unit) }

    fun requestRefresh(appWidgetIds: Set<Int>) {
        synchronized(refreshLock) {
            appWidgetIds.filter { it > 0 }.forEach { pendingRefreshes[it] = ++refreshVersion }
        }
        request()
    }

    suspend fun run() {
        var lastDeliveredSignature: String? = null
        for (ignored in requests) {
            var retryMs = 250L
            while (true) {
                try {
                    val snapshot = currentSnapshot()
                    val signature = snapshot.storedSnapshotSignature()
                    val isCurrent = {
                        currentSnapshot().storedSnapshotSignature() == signature
                    }
                    val delivered = signature == lastDeliveredSignature || publish(snapshot, isCurrent)
                    if (!isCurrent()) continue
                    if (delivered) lastDeliveredSignature = signature
                    // A resize/configuration event must render even if the paused word and its
                    // size class are unchanged. Revisions preserve a newer resize during delivery.
                    val refreshing = synchronized(refreshLock) {
                        pendingRefreshes.entries.take(3).associate { it.key to it.value }
                    }
                    val completed = if (refreshing.isEmpty()) emptySet() else
                        refresh(snapshot, refreshing.keys, isCurrent)
                    val stillPending = synchronized(refreshLock) {
                        refreshing.forEach { (id, version) ->
                            if (pendingRefreshes[id] == version) {
                                pendingRefreshes.remove(id)
                                // Rotate failures behind other IDs, so one broken instance cannot
                                // starve later widgets through repeated timeouts.
                                if (id !in completed) pendingRefreshes[id] = version
                            }
                        }
                        pendingRefreshes.isNotEmpty()
                    }
                    if (!isCurrent()) continue
                    if (delivered && !stillPending) break
                    if (completed.isNotEmpty()) continue
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    onFailure(error)
                }
                // New semantic events interrupt recovery backoff; retries always sample NOW.
                withTimeoutOrNull(retryMs) { requests.receive() }
                retryMs = (retryMs * 2).coerceAtMost(4_000L)
            }
        }
    }
}
