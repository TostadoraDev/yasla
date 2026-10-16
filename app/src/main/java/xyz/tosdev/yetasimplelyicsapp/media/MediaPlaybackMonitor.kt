package xyz.tosdev.yetasimplelyicsapp.media

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.database.ContentObserver
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import xyz.tosdev.yetasimplelyicsapp.debugTrace
import xyz.tosdev.yetasimplelyicsapp.data.artwork.ArtworkCache
import xyz.tosdev.yetasimplelyicsapp.domain.playback.PlaybackAction
import xyz.tosdev.yetasimplelyicsapp.domain.playback.PlaybackSnapshot
import xyz.tosdev.yetasimplelyicsapp.domain.playback.PlaybackStatus
import xyz.tosdev.yetasimplelyicsapp.domain.playback.PositionQuality
import xyz.tosdev.yetasimplelyicsapp.domain.playback.effectivePlaybackSpeed
import xyz.tosdev.yetasimplelyicsapp.domain.playback.TrackMetadata as DomainTrackMetadata

internal data class PlaybackMonitorDebugState(
    val activeSessionsListenerAlive: Boolean,
    val controllerCallbacksAlive: Int,
    val selectedSessionId: String?,
)

class MediaPlaybackMonitor private constructor(
    context: Context,
    private val arbitrator: PlaybackSessionArbitrator = PlaybackSessionArbitrator(),
) {
    private val applicationContext = context.applicationContext
    private val manager = applicationContext.getSystemService(MediaSessionManager::class.java)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val artworkCache = ArtworkCache(applicationContext)
    private val artworkScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val artworkFingerprints = ConcurrentHashMap<String, String>()
    private val cachedArtworkUris = ConcurrentHashMap<String, String>()
    private val listenerComponent = ComponentName(
        applicationContext,
        MediaNotificationListenerService::class.java,
    )
    private val entries = LinkedHashMap<String, ControllerEntry>()
    private var listening = false
    private var selectedSessionId: String? = null
    private var selectedTrackKey: String? = null
    private var generation = 0L
    private var accessObserverRegistered = false
    private val destroyedTokens = HashSet<MediaSession.Token>()
    private var recoveryAttempt = 0
    private var pendingRecovery: Runnable? = null
    private val accessObserver = object : ContentObserver(mainHandler) {
        override fun onChange(selfChange: Boolean) {
            recoveryAttempt = 0
            if (ensureSessionsListenerOnMain()) refreshSessionsOnMain(forceRebind = true)
        }
    }

    private val mutableState = MutableStateFlow(
        MediaPlaybackState(access = initialAccessState()),
    )
    val state: StateFlow<MediaPlaybackState> = mutableState.asStateFlow()

    private val sessionsChangedListener =
        MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
            debugTrace(applicationContext, TAG) {
                "active sessions changed count=${controllers.orEmpty().size}"
            }
            replaceControllers(controllers.orEmpty())
        }

    /**
     * Starts the system callback graph even when the process was recreated by a widget or the
     * launcher before the activity. Registration is idempotent and becomes idle when no session
     * exists; it does not poll.
     */
    fun startListening() {
        mainHandler.post {
            if (!accessObserverRegistered) {
                applicationContext.contentResolver.registerContentObserver(
                    Settings.Secure.getUriFor("enabled_notification_listeners"), false, accessObserver,
                )
                accessObserverRegistered = true
            }
            debugTrace(applicationContext, TAG) { "playback listener start requested" }
            if (ensureSessionsListenerOnMain()) refreshSessionsOnMain()
        }
    }

    fun onNotificationListenerConnected() {
        mainHandler.post {
            // A platform reconnect can preserve the same token but invalidate its callback graph.
            pendingRecovery?.let(mainHandler::removeCallbacks)
            pendingRecovery = null
            recoveryAttempt = 0
            removeSessionsListener()
            if (ensureSessionsListenerOnMain()) refreshSessionsOnMain(forceRebind = true)
        }
    }

    fun onNotificationListenerDisconnected() {
        mainHandler.post {
            if (!hasNotificationAccess()) {
                invalidateAccess()
            } else {
                // A transient NotificationListenerService disconnect must not tear down valid
                // MediaController callbacks. Re-register the active-session listener explicitly
                // so a stale platform registration cannot leave the widget dormant.
                removeSessionsListener()
                if (ensureSessionsListenerOnMain()) refreshSessionsOnMain(forceRebind = true)
            }
        }
    }

    fun refreshSessions() {
        mainHandler.post {
            if (ensureSessionsListenerOnMain()) refreshSessionsOnMain()
        }
    }

    internal fun debugState(): PlaybackMonitorDebugState = PlaybackMonitorDebugState(
        activeSessionsListenerAlive = listening,
        controllerCallbacksAlive = entries.size,
        selectedSessionId = selectedSessionId,
    )

    internal fun traceLiveness(reason: String) {
        mainHandler.post {
            val status = debugState()
            debugTrace(applicationContext, TAG) {
                "PlaybackMonitor alive reason=$reason listener=" +
                    "${status.activeSessionsListenerAlive} callbacks=" +
                    "${status.controllerCallbacksAlive} session=" +
                    status.selectedSessionId.orEmpty()
            }
        }
    }

    private fun ensureSessionsListenerOnMain(): Boolean {
        if (!hasNotificationAccess()) {
            invalidateAccess()
            return false
        }
        if (listening) return true
        return try {
            manager.addOnActiveSessionsChangedListener(
                sessionsChangedListener,
                listenerComponent,
                mainHandler,
            )
            listening = true
            debugTrace(applicationContext, TAG) { "active sessions listener registered" }
            true
        } catch (error: SecurityException) {
            debugTrace(applicationContext, TAG) {
                "active sessions listener rejected: ${error.javaClass.simpleName}"
            }
            invalidateAccess()
            scheduleRecovery()
            false
        }
    }

    private fun refreshSessionsOnMain(forceRebind: Boolean = false) {
        try {
            replaceControllers(
                controllers = manager.getActiveSessions(listenerComponent),
                forceRebind = forceRebind,
            )
        } catch (error: SecurityException) {
            debugTrace(applicationContext, TAG) {
                "active sessions refresh rejected: ${error.javaClass.simpleName}"
            }
            invalidateAccess()
            scheduleRecovery()
        }
    }

    fun dispatch(command: TransportCommand): TransportResult {
        val controller = entries[selectedSessionId]?.controller
            ?: return TransportResult.NoActiveSession
        val capabilities = capabilities(controller.playbackState?.actions ?: 0L)
        return try {
            when (command) {
                TransportCommand.PLAY -> {
                    if (!capabilities.canPlay) return TransportResult.Unsupported
                    controller.transportControls.play()
                }
                TransportCommand.PAUSE -> {
                    if (!capabilities.canPause) return TransportResult.Unsupported
                    controller.transportControls.pause()
                }
                TransportCommand.PLAY_PAUSE -> {
                    val status = playbackStatus(controller.playbackState?.state)
                    if (status == PlaybackStatus.PLAYING) {
                        if (!capabilities.canPause) return TransportResult.Unsupported
                        controller.transportControls.pause()
                    } else {
                        if (!capabilities.canPlay) return TransportResult.Unsupported
                        controller.transportControls.play()
                    }
                }
                TransportCommand.NEXT -> {
                    if (!capabilities.canSkipNext) return TransportResult.Unsupported
                    controller.transportControls.skipToNext()
                }
                TransportCommand.PREVIOUS -> {
                    if (!capabilities.canSkipPrevious) return TransportResult.Unsupported
                    controller.transportControls.skipToPrevious()
                }
                TransportCommand.STOP -> {
                    if (!capabilities.canStop) return TransportResult.Unsupported
                    controller.transportControls.stop()
                }
            }
            TransportResult.Sent
        } catch (error: RuntimeException) {
            TransportResult.Failed(error.message ?: error.javaClass.simpleName)
        }
    }

    fun seekTo(positionMs: Long): TransportResult {
        val controller = entries[selectedSessionId]?.controller
            ?: return TransportResult.NoActiveSession
        val actions = controller.playbackState?.actions ?: 0L
        if (!capabilities(actions).canSeek) return TransportResult.Unsupported
        return try {
            controller.transportControls.seekTo(positionMs.coerceAtLeast(0L))
            TransportResult.Sent
        } catch (error: RuntimeException) {
            TransportResult.Failed(error.message ?: error.javaClass.simpleName)
        }
    }

    fun notificationAccessSettingsIntent(): Intent =
        Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")

    fun hasNotificationAccess(): Boolean {
        val enabled = Settings.Secure.getString(
            applicationContext.contentResolver,
            "enabled_notification_listeners",
        ).orEmpty()
        val expected = listenerComponent.flattenToString()
        return enabled.split(':').any { it == expected }
    }

    private fun replaceControllers(
        controllers: List<MediaController>,
        forceRebind: Boolean = false,
    ) {
        debugTrace(applicationContext, TAG) {
            "reconciling sessions count=${controllers.size} forceRebind=$forceRebind"
        }
        if (forceRebind) {
            entries.values.toList().forEach { removeController(it, "listener reconnect") }
        }
        destroyedTokens.retainAll(controllers.map { it.sessionToken }.toSet())
        val liveControllers = controllers.filterNot { it.sessionToken in destroyedTokens }
        val newIds = liveControllers.mapTo(HashSet()) { sessionId(it) }
        entries.values
            .filterNot { it.sessionId in newIds }
            .forEach { removeController(it, "session removed") }

        liveControllers.forEach { controller ->
            val id = sessionId(controller)
            if (entries[id] == null) {
                val callback = object : MediaController.Callback() {
                    override fun onPlaybackStateChanged(state: PlaybackState?) {
                        if (entries[id]?.callback !== this) return
                        debugTrace(applicationContext, TAG) {
                            "playback sample session=$id state=${state?.state} " +
                                "mediaAt=${state?.lastPositionUpdateTime} baseMs=${state?.position} " +
                                "speed=${state?.playbackSpeed} receivedAt=${SystemClock.elapsedRealtime()}"
                        }
                        publish()
                    }

                    override fun onMetadataChanged(metadata: MediaMetadata?) {
                        if (entries[id]?.callback !== this) return
                        debugTrace(applicationContext, TAG) {
                            "metadata changed session=$id hasTitle=" +
                                "${!metadata?.getString(MediaMetadata.METADATA_KEY_TITLE).isNullOrBlank()}"
                        }
                        publish()
                    }

                    override fun onSessionDestroyed() {
                        if (entries[id]?.callback !== this) return
                        destroyedTokens.add(controller.sessionToken)
                        debugTrace(applicationContext, TAG) { "session destroyed session=$id" }
                        entries[id]?.let { removeController(it, "session destroyed") }
                        if (selectedSessionId == id) selectedSessionId = null
                        publish()
                        refreshSessionsOnMain(forceRebind = true)
                    }
                }
                val registered = runCatching {
                    controller.registerCallback(callback, mainHandler)
                }.isSuccess
                if (registered) {
                    entries[id] = ControllerEntry(id, controller, callback)
                    debugTrace(applicationContext, TAG) {
                        "callback registered session=$id package=${controller.packageName}"
                    }
                } else {
                    debugTrace(applicationContext, TAG) {
                        "callback registration failed session=$id package=${controller.packageName}"
                    }
                    scheduleRecovery()
                }
            }
        }
        if (liveControllers.all { entries[sessionId(it)] != null }) {
            recoveryAttempt = 0
            pendingRecovery?.let(mainHandler::removeCallbacks)
            pendingRecovery = null
        }
        publish()
    }

    private fun publish() {
        val now = SystemClock.elapsedRealtime()
        val candidates = entries.values.map { entry ->
            val metadata = metadata(entry.controller.metadata)
            val state = entry.controller.playbackState
            SessionCandidate(
                sessionId = entry.sessionId,
                packageName = entry.controller.packageName,
                status = playbackStatus(state?.state),
                updatedAtElapsedMs = state?.lastPositionUpdateTime?.takeIf { it > 0L } ?: now,
                hasTitle = !metadata.title.isNullOrBlank(),
                hasArtist = !metadata.artist.isNullOrBlank(),
                hasMediaId = !metadata.mediaId.isNullOrBlank(),
            )
        }
        val previousSessionId = selectedSessionId
        val selected = arbitrator.select(candidates, selectedSessionId, now)
        selectedSessionId = selected?.sessionId
        if (selectedSessionId != previousSessionId) {
            debugTrace(applicationContext, TAG) {
                "selected controller session=${selectedSessionId.orEmpty()} package=" +
                    "${selected?.packageName.orEmpty()}"
            }
        }
        val active = selected?.let { candidate ->
            entries[candidate.sessionId]?.controller?.let { toActivePlayback(it, now) }
        }
        mutableState.value = MediaPlaybackState(
            access = if (active == null) {
                MediaAccessState.NO_ACTIVE_SESSION
            } else {
                MediaAccessState.CONNECTED
            },
            active = active,
            candidateCount = candidates.size,
        )
    }

    private fun toActivePlayback(
        controller: MediaController,
        nowElapsedMs: Long,
    ): ActivePlayback {
        val sourceMetadata = controller.metadata
        val rawMetadata = metadata(sourceMetadata)
        val state = controller.playbackState
        val status = playbackStatus(state?.state)
        val trackKey = buildTrackKey(controller.packageName, rawMetadata)
        cacheArtwork(trackKey, sourceMetadata, rawMetadata.artworkUri)
        val cachedArtworkUri = cachedArtworkUris[trackKey]
        val metadata = rawMetadata.copy(
            artworkUri = cachedArtworkUri ?: rawMetadata.artworkUri,
            isArtworkCached = cachedArtworkUri != null,
        )
        if (trackKey != selectedTrackKey) {
            selectedTrackKey = trackKey
            generation += 1L
        }
        val actions = state?.actions ?: 0L
        val session = sessionId(controller)
        return ActivePlayback(
            packageName = controller.packageName,
            sessionId = session,
            track = metadata,
            playback = PlaybackSnapshot(
                sessionId = session,
                generation = generation,
                trackKey = trackKey,
                status = status,
                reportedPositionMs = state?.position?.takeIf { it >= 0L },
                reportedAtElapsedMs = state?.lastPositionUpdateTime
                    ?.takeIf { it > 0L }
                    ?: nowElapsedMs,
                // Several players publish STATE_PLAYING with a missing/zero speed. Treat that
                // platform omission as normal 1x playback so the application-scoped clock and
                // one-shot widget boundary scheduler continue while the Activity is stopped.
                speed = effectivePlaybackSpeed(status, state?.playbackSpeed),
                durationMs = metadata.durationMs,
                isSeekable = capabilities(actions).canSeek,
                playerPackage = controller.packageName,
                metadata = metadata.toDomain(trackKey),
                actions = actions.toDomainActions(),
                artworkUri = metadata.artworkUri,
                positionQuality = if (state?.position != null && state.position >= 0L) {
                    if (status == PlaybackStatus.PLAYING) {
                        PositionQuality.EXTRAPOLATED
                    } else {
                        PositionQuality.EXACT
                    }
                } else if (metadata.hasIdentity) {
                    PositionQuality.METADATA_ONLY
                } else {
                    PositionQuality.UNKNOWN
                },
            ),
            capabilities = capabilities(actions),
        )
    }

    private fun cacheArtwork(
        trackKey: String,
        metadata: MediaMetadata?,
        artworkUri: String?,
    ) {
        val bitmap = metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_ART)
        if (artworkUri.isNullOrBlank() && bitmap == null) return

        val fingerprint = artworkUri?.takeIf(String::isNotBlank)
            ?: "bitmap:${bitmap?.width}x${bitmap?.height}:${bitmap?.generationId}"
        if (artworkFingerprints.put(trackKey, fingerprint) == fingerprint) return

        artworkScope.launch {
            val cachedUri = runCatching {
                artworkCache.cache(trackKey, artworkUri, bitmap)
            }.getOrNull() ?: return@launch
            cachedArtworkUris[trackKey] = cachedUri
            mainHandler.post {
                if (selectedTrackKey == trackKey) publish()
            }
        }
    }

    private fun unregisterControllers() {
        entries.values.toList().forEach { removeController(it, "listener stopped") }
    }

    private fun removeSessionsListener() {
        if (listening) runCatching { manager.removeOnActiveSessionsChangedListener(sessionsChangedListener) }
        listening = false
    }

    private fun invalidateAccess() {
        pendingRecovery?.let(mainHandler::removeCallbacks)
        pendingRecovery = null
        removeSessionsListener()
        unregisterControllers()
        selectedSessionId = null
        selectedTrackKey = null
        mutableState.value = MediaPlaybackState(access = if (hasNotificationAccess()) {
            MediaAccessState.CONNECTING
        } else {
            MediaAccessState.PERMISSION_REQUIRED
        })
    }

    // Failure-triggered recovery. Healthy subscriptions never poll; prolonged failures back off
    // to one retry per minute while Android keeps the listener process legitimately active.
    private fun scheduleRecovery() {
        if (!hasNotificationAccess() || pendingRecovery != null) return
        val delays = longArrayOf(250L, 1_000L, 4_000L, 15_000L, 60_000L)
        val retryDelay = delays[recoveryAttempt.coerceAtMost(delays.lastIndex)]
        recoveryAttempt = (recoveryAttempt + 1).coerceAtMost(delays.lastIndex)
        val retry = Runnable {
            pendingRecovery = null
            debugTrace(applicationContext, TAG) { "subscription recovery attempt=$recoveryAttempt" }
            if (ensureSessionsListenerOnMain()) refreshSessionsOnMain(forceRebind = true)
        }
        pendingRecovery = retry
        mainHandler.postDelayed(retry, retryDelay)
    }

    private fun removeController(entry: ControllerEntry, reason: String) {
        runCatching { entry.controller.unregisterCallback(entry.callback) }
        entries.remove(entry.sessionId)
        debugTrace(applicationContext, TAG) {
            "callback removed session=${entry.sessionId} reason=$reason"
        }
    }

    private fun initialAccessState(): MediaAccessState =
        if (hasNotificationAccess()) {
            MediaAccessState.CONNECTING
        } else {
            MediaAccessState.PERMISSION_REQUIRED
        }

    private fun sessionId(controller: MediaController): String =
        controller.packageName + ":" + controller.sessionToken.hashCode()

    private fun buildTrackKey(packageName: String, metadata: TrackMetadata): String {
        val identity = metadata.mediaId?.takeIf(String::isNotBlank)
            ?: listOf(metadata.title, metadata.artist, metadata.album, metadata.durationMs)
                .joinToString("|") { it?.toString().orEmpty() }
        return "$packageName:$identity"
    }

    private fun metadata(value: MediaMetadata?): TrackMetadata = TrackMetadata(
        title = value?.getString(MediaMetadata.METADATA_KEY_TITLE),
        artist = value?.getString(MediaMetadata.METADATA_KEY_ARTIST),
        album = value?.getString(MediaMetadata.METADATA_KEY_ALBUM),
        mediaId = value?.getString(MediaMetadata.METADATA_KEY_MEDIA_ID),
        artworkUri = value?.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI)
            ?: value?.getString(MediaMetadata.METADATA_KEY_ART_URI),
        durationMs = value?.getLong(MediaMetadata.METADATA_KEY_DURATION)
            ?.takeIf { it > 0L },
    )

    private fun TrackMetadata.toDomain(trackKey: String): DomainTrackMetadata? {
        val titleValue = title?.trim().orEmpty()
        val artistValue = artist?.trim().orEmpty()
        if (titleValue.isBlank() || artistValue.isBlank()) return null
        return DomainTrackMetadata(
            trackKey = trackKey,
            title = titleValue,
            artists = listOf(artistValue),
            album = album?.takeIf(String::isNotBlank),
            durationMs = durationMs,
            artworkUri = artworkUri,
        )
    }

    private fun Long.toDomainActions(): Set<PlaybackAction> = buildSet {
        val value = capabilities(this@toDomainActions)
        if (value.canPlay) add(PlaybackAction.PLAY)
        if (value.canPause) add(PlaybackAction.PAUSE)
        if (value.canSeek) add(PlaybackAction.SEEK)
        if (value.canSkipNext) add(PlaybackAction.SKIP_NEXT)
        if (value.canSkipPrevious) add(PlaybackAction.SKIP_PREVIOUS)
        if (value.canStop) add(PlaybackAction.STOP)
    }

    private fun playbackStatus(state: Int?): PlaybackStatus = when (state) {
        PlaybackState.STATE_PLAYING -> PlaybackStatus.PLAYING
        PlaybackState.STATE_PAUSED -> PlaybackStatus.PAUSED
        PlaybackState.STATE_BUFFERING,
        PlaybackState.STATE_CONNECTING,
        -> PlaybackStatus.BUFFERING
        PlaybackState.STATE_STOPPED -> PlaybackStatus.STOPPED
        else -> PlaybackStatus.IDLE
    }

    private fun capabilities(actions: Long): TransportCapabilities = TransportCapabilities(
        canPlay = actions hasAny (
            PlaybackState.ACTION_PLAY or
                PlaybackState.ACTION_PLAY_PAUSE or
                PlaybackState.ACTION_PLAY_FROM_MEDIA_ID or
                PlaybackState.ACTION_PLAY_FROM_SEARCH or
                PlaybackState.ACTION_PLAY_FROM_URI
            ),
        canPause = actions hasAny (PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_PLAY_PAUSE),
        canSeek = actions hasAny PlaybackState.ACTION_SEEK_TO,
        canSkipNext = actions hasAny PlaybackState.ACTION_SKIP_TO_NEXT,
        canSkipPrevious = actions hasAny PlaybackState.ACTION_SKIP_TO_PREVIOUS,
        canStop = actions hasAny PlaybackState.ACTION_STOP,
    )

    private infix fun Long.hasAny(mask: Long): Boolean = this and mask != 0L

    private data class ControllerEntry(
        val sessionId: String,
        val controller: MediaController,
        val callback: MediaController.Callback,
    )

    companion object {
        private const val TAG = "MediaPlaybackMonitor"

        @Volatile
        private var instance: MediaPlaybackMonitor? = null

        fun get(context: Context): MediaPlaybackMonitor =
            instance ?: synchronized(this) {
                instance ?: MediaPlaybackMonitor(context).also { instance = it }
            }
    }
}
