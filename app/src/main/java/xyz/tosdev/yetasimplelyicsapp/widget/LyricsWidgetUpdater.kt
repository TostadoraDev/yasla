package xyz.tosdev.yetasimplelyicsapp.widget

import android.content.Context
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.os.SystemClock
import androidx.compose.ui.unit.DpSize
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.compose
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import xyz.tosdev.yetasimplelyicsapp.debugTrace
import xyz.tosdev.yetasimplelyicsapp.LyricsApplication

private const val WIDGET_UPDATER_TAG = "LyricsWidgetUpdater"
private const val WIDGET_PUBLISH_TIMEOUT_MS = 8_000L
private enum class InstanceDelivery { UNCHANGED, SUBMITTED, OBSOLETE }

private fun String.sanitizedWidgetDebugText(): String =
    replace('\r', ' ').replace('\n', ' ').take(80)

private data class CachedWidgetInstance(
    val appWidgetId: Int,
    val glanceId: GlanceId,
    val config: LyricsWidgetConfig,
    val sizes: List<DpSize>,
    val sizeClasses: Set<WidgetSizeClass>,
    val generation: Long,
    var renderedSignature: String,
)

private data class DurableWidgetSnapshot(
    val context: Context,
    val instance: CachedWidgetInstance,
    val snapshot: LyricsWidgetSnapshot,
    val signature: String,
)

private data class WidgetWordPrewarm(
    val context: Context,
    val config: LyricsWidgetConfig,
    val snapshot: LyricsWidgetSnapshot,
    val sizes: List<DpSize>,
)

object LyricsWidgetUpdater {
    private val updateMutex = Mutex()
    private val instanceCache = ConcurrentHashMap<Int, CachedWidgetInstance>()
    private val instanceGenerations = ConcurrentHashMap<Int, Long>()
    private val generationSequence = AtomicLong()
    private val obsoleteCompositions = AtomicLong()
    private val backgroundScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val durableWriter = DurableSnapshotWriter(backgroundScope)
    private val wordPrewarmer = CurrentWordPrewarmer(
        CoroutineScope(SupervisorJob() + Dispatchers.Default),
    )

    /** Invalidates process-only data; persisted configuration remains the restart source of truth. */
    internal fun invalidate(appWidgetId: Int) {
        instanceGenerations[appWidgetId] = generationSequence.incrementAndGet()
        instanceCache.remove(appWidgetId)
    }

    private fun generationFor(appWidgetId: Int): Long =
        instanceGenerations.computeIfAbsent(appWidgetId) { generationSequence.incrementAndGet() }

    private fun isGenerationCurrent(appWidgetId: Int, generation: Long): Boolean =
        instanceGenerations[appWidgetId] == generation

    /**
     * Publishes a passive display snapshot to every placed instance. Each instance owns its
     * configuration and independently decides whether the current line changes its render.
     */
    suspend fun publish(
        context: Context,
        snapshot: LyricsWidgetSnapshot,
        isCurrent: () -> Boolean = { true },
    ): Boolean {
        val applicationContext = context.applicationContext
        return try {
            // Include mutex acquisition and Glance ID enumeration in the deadline. Previously
            // getGlanceIds() could hang the single conflated dispatcher forever in background.
            withTimeout(WIDGET_PUBLISH_TIMEOUT_MS) {
                updateMutex.withLock {
                    val manager = GlanceAppWidgetManager(applicationContext)
                    var delivered = true
                    val installedIds = installedIds(applicationContext).toSet()
                    instanceCache.keys
                        .filter { it !in installedIds }
                        .forEach(::invalidate)
                    installedIds.forEach { appWidgetId ->
                        try {
                            val rendered = publishToInstance(
                                applicationContext,
                                manager,
                                appWidgetId,
                                snapshot,
                                isCurrent,
                            )
                            if (rendered == InstanceDelivery.OBSOLETE) delivered = false
                            if (rendered == InstanceDelivery.SUBMITTED) {
                                debugTrace(applicationContext, WIDGET_UPDATER_TAG) {
                                    "widget update submitted id=$appWidgetId word=" +
                                        "${snapshot.activeWordId.hashCode()} line=${snapshot.activeLineId.hashCode()}"
                                }
                            }
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (error: Exception) {
                            delivered = false
                            debugTrace(applicationContext, WIDGET_UPDATER_TAG) {
                                "widget update failed id=$appWidgetId error=" +
                                    error.javaClass.simpleName
                            }
                            // Keep the other instances fresh. This instance remains pending.
                        }
                    }
                    delivered
                }
            }
        } catch (_: TimeoutCancellationException) {
            debugTrace(applicationContext, WIDGET_UPDATER_TAG) {
                "widget publish timed out before completion"
            }
            false
        } catch (cancelled: CancellationException) {
            throw cancelled
        }
    }

    suspend fun updateConfiguration(
        context: Context,
        appWidgetId: Int,
        config: LyricsWidgetConfig,
    ) = withTimeout(WIDGET_PUBLISH_TIMEOUT_MS) { updateMutex.withLock {
        val applicationContext = context.applicationContext
        val manager = GlanceAppWidgetManager(applicationContext)
        val glanceId = manager.getGlanceIdBy(appWidgetId)
        invalidate(appWidgetId)
        LyricsWidgetState.saveConfig(applicationContext, glanceId, config)
        val state = LyricsWidgetState.read(applicationContext, glanceId)
        val sizeClasses = manager.widgetSizeClasses(glanceId)
        val snapshot = liveSnapshot(applicationContext) ?: state.snapshot
        LyricsWidgetState.saveSnapshotIfRenderNeeded(applicationContext, glanceId, snapshot, sizeClasses)
        val signature = snapshot.lineRenderSignature(state.config, sizeClasses)
        composeAndSubmit(applicationContext, manager, glanceId, snapshot)
        LyricsWidgetState.markRendered(applicationContext, glanceId, signature, sizeClasses)
    } }

    /** Forced geometry/locale refresh. Successful IDs are acknowledged individually. */
    internal suspend fun refresh(
        context: Context,
        snapshot: LyricsWidgetSnapshot,
        appWidgetIds: Set<Int>,
        isCurrent: () -> Boolean,
    ): Set<Int> {
        val completed = mutableSetOf<Int>()
        val applicationContext = context.applicationContext
        val manager = GlanceAppWidgetManager(applicationContext)
        val platform = AppWidgetManager.getInstance(applicationContext)
        try {
            withTimeout(WIDGET_PUBLISH_TIMEOUT_MS) { updateMutex.withLock {
                appWidgetIds.forEach { id ->
                    try {
                        val submitted = withTimeoutOrNull(2_000L) {
                            // Deleted while pending: resolve it. Binder failures are isolated by ID.
                            if (platform.getAppWidgetInfo(id) == null) {
                                invalidate(id)
                                return@withTimeoutOrNull true
                            }
                            invalidate(id)
                            val glanceId = manager.getGlanceIdBy(id)
                            val state = LyricsWidgetState.read(applicationContext, glanceId)
                            val classes = manager.widgetSizeClasses(glanceId)
                            LyricsWidgetState.saveSnapshotIfRenderNeeded(applicationContext, glanceId, snapshot, classes)
                            if (!composeAndSubmit(applicationContext, manager, glanceId, snapshot, isCurrent)) {
                                false
                            } else {
                                LyricsWidgetState.markRendered(applicationContext, glanceId,
                                    snapshot.lineRenderSignature(state.config, classes), classes)
                                true
                            }
                        } == true
                        if (submitted) completed += id
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Exception) {
                        debugTrace(applicationContext, WIDGET_UPDATER_TAG) {
                            "widget refresh failed id=$id error=${error.javaClass.simpleName}"
                        }
                    }
                }
            } }
        } catch (_: TimeoutCancellationException) {
            debugTrace(applicationContext, WIDGET_UPDATER_TAG) { "widget refresh timed out; pending IDs retained" }
        }
        return completed
    }

    private suspend fun publishToInstance(
        context: Context,
        manager: GlanceAppWidgetManager,
        appWidgetId: Int,
        snapshot: LyricsWidgetSnapshot,
        isCurrent: () -> Boolean,
    ): InstanceDelivery {
        val instance = cachedInstance(context, manager, appWidgetId)
        val signature = snapshot.lineRenderSignature(instance.config, instance.sizeClasses)
        if (instance.renderedSignature == signature) return InstanceDelivery.UNCHANGED
        val preparationStartedAt = SystemClock.elapsedRealtime()
        val nativeViews = withContext(Dispatchers.Default) {
            currentWordRemoteViews(
                context,
                instance.config,
                snapshot,
                instance.sizes,
            )
        }
        if (nativeViews != null) {
            val preparationDurationMs = SystemClock.elapsedRealtime() - preparationStartedAt
            val submitted = submitRemoteViews(
                context = context,
                appWidgetId = appWidgetId,
                remoteViews = nativeViews,
                snapshot = snapshot,
                preparationStartedAt = preparationStartedAt,
                preparationDurationMs = preparationDurationMs,
                isCurrent = isCurrent,
            )
            if (!submitted) return InstanceDelivery.OBSOLETE
            instance.renderedSignature = signature
            durableWriter.enqueue(DurableWidgetSnapshot(context, instance, snapshot, signature))
            wordPrewarmer.enqueue(WidgetWordPrewarm(context, instance.config, snapshot, instance.sizes))
            return InstanceDelivery.SUBMITTED
        }

        // Other layouts keep Glance's durable state flow. Their cadence is line-based and does not
        // need the per-word native fast path.
        val glanceId = instance.glanceId
        val sizeClasses = instance.sizeClasses
        val pendingSignature = LyricsWidgetState.saveSnapshotIfRenderNeeded(
            context,
            glanceId,
            snapshot,
            sizeClasses,
        )
            ?: return InstanceDelivery.UNCHANGED
        if (!composeAndSubmit(context, manager, glanceId, snapshot, isCurrent)) return InstanceDelivery.OBSOLETE
        LyricsWidgetState.markRendered(context, glanceId, pendingSignature, sizeClasses)
        instance.renderedSignature = pendingSignature
        return InstanceDelivery.SUBMITTED
    }

    private suspend fun cachedInstance(
        context: Context,
        manager: GlanceAppWidgetManager,
        appWidgetId: Int,
    ): CachedWidgetInstance {
        instanceCache[appWidgetId]?.let { cached ->
            if (isGenerationCurrent(appWidgetId, cached.generation)) return cached
        }
        val glanceId = manager.getGlanceIdBy(appWidgetId)
        val state = LyricsWidgetState.read(context, glanceId)
        val sizes = manager.getAppWidgetSizes(glanceId)
            .filter { it.width.value > 0f && it.height.value > 0f }
            .distinct()
        val sizeClasses = sizes.toWidgetSizeClasses()
        return CachedWidgetInstance(
            appWidgetId = appWidgetId,
            glanceId = glanceId,
            config = state.config,
            sizes = sizes,
            sizeClasses = sizeClasses,
            generation = generationFor(appWidgetId),
            renderedSignature = state.renderedSignature,
        ).also { instanceCache[appWidgetId] = it }
    }

    private suspend fun liveSnapshot(context: Context): LyricsWidgetSnapshot? =
        withContext(Dispatchers.Main.immediate) {
            (context.applicationContext as? LyricsApplication)?.container?.currentWidgetSnapshot()
        }

    /** Compose in the live process; update() only enqueues an asynchronous Glance session. */
    private suspend fun composeAndSubmit(
        context: Context,
        manager: GlanceAppWidgetManager,
        glanceId: GlanceId,
        snapshot: LyricsWidgetSnapshot,
        isCurrent: () -> Boolean = { true },
    ): Boolean = withContext(Dispatchers.Default) {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val id = manager.getAppWidgetId(glanceId)
        val startedAt = SystemClock.elapsedRealtime()
        val preferences = getAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId)
        val config = LyricsWidgetState.fromPreferences(preferences).config
        val remoteViews = currentWordRemoteViews(context, config, snapshot, manager.getAppWidgetSizes(glanceId))
            ?: LyricsWidget().compose(
            context = context,
            id = glanceId,
            options = appWidgetManager.getAppWidgetOptions(id),
            state = preferences,
        )
        submitRemoteViews(
            context = context,
            appWidgetId = id,
            remoteViews = remoteViews,
            snapshot = snapshot,
            preparationStartedAt = startedAt,
            preparationDurationMs = SystemClock.elapsedRealtime() - startedAt,
            isCurrent = isCurrent,
        )
    }

    private suspend fun submitRemoteViews(
        context: Context,
        appWidgetId: Int,
        remoteViews: android.widget.RemoteViews,
        snapshot: LyricsWidgetSnapshot,
        preparationStartedAt: Long,
        preparationDurationMs: Long,
        isCurrent: () -> Boolean,
    ): Boolean {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val submitted = withContext(Dispatchers.Main.immediate) {
            if (!isCurrent()) false else {
                appWidgetManager.updateAppWidget(appWidgetId, remoteViews)
                true
            }
        }
        if (!submitted) {
            val count = obsoleteCompositions.incrementAndGet()
            WidgetDeliveryDiagnostics.recordObsolete(context, count)
            debugTrace(context, WIDGET_UPDATER_TAG) {
                "obsolete composition discarded id=$appWidgetId count=$count"
            }
            return false
        }
        val submittedAt = SystemClock.elapsedRealtime()
        val requestedAt = snapshot.renderRequestedAtElapsedMs.takeIf { it >= 0L }
            ?: snapshot.capturedAtElapsedMs
        WidgetDeliveryDiagnostics.record(
            context,
            WidgetDeliveryTrace(
                appWidgetId = appWidgetId,
                wordIdHash = snapshot.activeWordId.hashCode(),
                sampleAtElapsedMs = snapshot.capturedAtElapsedMs,
                submittedAtElapsedMs = submittedAt,
                sourceTimestampMs = snapshot.activeWordSourceStartMs,
                scheduledAtElapsedMs = snapshot.activeWordScheduledAtElapsedMs,
                requestedAtElapsedMs = requestedAt,
                preparationStartedAtElapsedMs = preparationStartedAt,
                preparationDurationMs = preparationDurationMs,
                obsoleteCount = obsoleteCompositions.get(),
            ),
        )
        debugTrace(context, WIDGET_UPDATER_TAG) {
            "widget binder submit id=$appWidgetId " +
                "expectedLyricsWord=${snapshot.activeWord.sanitizedWidgetDebugText()} " +
                "expectedWordId=${snapshot.activeWordId.hashCode()} " +
                "sourceAt=${snapshot.activeWordSourceStartMs} " +
                "scheduledAt=${snapshot.activeWordScheduledAtElapsedMs} requestedAt=$requestedAt " +
                "sampleAt=${snapshot.capturedAtElapsedMs} prepareStartedAt=$preparationStartedAt " +
                "submittedAt=$submittedAt internalLatencyMs=${submittedAt - requestedAt} " +
                "prepareMs=$preparationDurationMs obsoleteCount=${obsoleteCompositions.get()} " +
                "launcherAck=unavailable"
        }
        return true
    }

    private suspend fun GlanceAppWidgetManager.widgetSizeClasses(
        glanceId: GlanceId,
    ): Set<WidgetSizeClass> = getAppWidgetSizes(glanceId).toWidgetSizeClasses()

    private fun List<DpSize>.toWidgetSizeClasses(): Set<WidgetSizeClass> {
        val classes = filter { it.width.value > 0f && it.height.value > 0f }
            .mapTo(linkedSetOf()) { size ->
                widgetSizeClass(size.width.value, size.height.value)
            }
        return if (classes.isEmpty()) WidgetSizeClass.entries.toSet() else classes
    }

    private class DurableSnapshotWriter(scope: CoroutineScope) {
        private val lock = Any()
        private val pending = linkedMapOf<Int, DurableWidgetSnapshot>()
        private val failed = linkedMapOf<Int, DurableWidgetSnapshot>()
        private val requests = Channel<Unit>(Channel.CONFLATED)

        init {
            scope.launch {
                @Suppress("UNUSED_VARIABLE")
                for (signal in requests) {
                    while (true) {
                        val request = synchronized(lock) {
                            val entry = pending.entries.firstOrNull() ?: return@synchronized null
                            pending.remove(entry.key)
                            entry.value
                        } ?: break
                        if (!LyricsWidgetUpdater.isGenerationCurrent(
                                request.instance.appWidgetId,
                                request.instance.generation,
                            )
                        ) continue
                        try {
                            LyricsWidgetState.persistDeliveredSnapshot(
                                context = request.context,
                                glanceId = request.instance.glanceId,
                                snapshot = request.snapshot,
                                signature = request.signature,
                                sizeClasses = request.instance.sizeClasses,
                                shouldPersist = {
                                    LyricsWidgetUpdater.isGenerationCurrent(
                                        request.instance.appWidgetId,
                                        request.instance.generation,
                                    )
                                },
                            )
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (error: Exception) {
                            synchronized(lock) {
                                if (LyricsWidgetUpdater.isGenerationCurrent(
                                        request.instance.appWidgetId,
                                        request.instance.generation,
                                    )
                                ) failed[request.instance.appWidgetId] = request
                            }
                            debugTrace(request.context, WIDGET_UPDATER_TAG) {
                                "deferred widget state failed id=${request.instance.appWidgetId} " +
                                    "error=${error.javaClass.simpleName}"
                            }
                        }
                    }
                }
            }
        }

        fun enqueue(request: DurableWidgetSnapshot) {
            synchronized(lock) {
                failed.entries.forEach { (id, failedRequest) ->
                    if (LyricsWidgetUpdater.isGenerationCurrent(id, failedRequest.instance.generation)) {
                        pending.putIfAbsent(id, failedRequest)
                    }
                }
                failed.clear()
                pending[request.instance.appWidgetId] = request
            }
            requests.trySend(Unit)
        }
    }

    private class CurrentWordPrewarmer(scope: CoroutineScope) {
        private val requests = Channel<WidgetWordPrewarm>(Channel.CONFLATED)

        init {
            scope.launch {
                for (request in requests) {
                    if (request.snapshot.nextWordScheduledAtElapsedMs in 0L..SystemClock.elapsedRealtime()) {
                        continue
                    }
                    runCatching {
                        prewarmNextCurrentWord(
                            request.context,
                            request.config,
                            request.snapshot,
                            request.sizes,
                        )
                    }
                }
            }
        }

        fun enqueue(request: WidgetWordPrewarm) {
            if (request.snapshot.nextWordId.isNotBlank() && request.snapshot.nextWord.isNotBlank()) {
                requests.trySend(request)
            }
        }
    }

    suspend fun installedGlanceIds(context: Context): List<GlanceId> =
        AppWidgetManager.getInstance(context).getAppWidgetIds(
            ComponentName(context, LyricsWidgetReceiver::class.java),
        ).map { GlanceAppWidgetManager(context).getGlanceIdBy(it) }

    suspend fun installedIds(context: Context): IntArray {
        return AppWidgetManager.getInstance(context).getAppWidgetIds(
            ComponentName(context, LyricsWidgetReceiver::class.java),
        )
    }
}
