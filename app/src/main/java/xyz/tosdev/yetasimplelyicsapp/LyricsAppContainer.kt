package xyz.tosdev.yetasimplelyicsapp

import android.app.KeyguardManager
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.PowerManager
import android.os.SystemClock
import android.provider.OpenableColumns
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.platform.AndroidUiDispatcher
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import xyz.tosdev.yetasimplelyicsapp.data.importing.LyricsImportReader
import xyz.tosdev.yetasimplelyicsapp.data.importing.LyricsImportException
import xyz.tosdev.yetasimplelyicsapp.data.importing.LyricsImportFailure
import xyz.tosdev.yetasimplelyicsapp.data.local.LyricsDatabase
import xyz.tosdev.yetasimplelyicsapp.data.local.LyricsLibraryItem
import xyz.tosdev.yetasimplelyicsapp.data.local.RoomLyricsStore
import xyz.tosdev.yetasimplelyicsapp.data.preferences.AppPreferences
import xyz.tosdev.yetasimplelyicsapp.data.preferences.VisualPreferences
import xyz.tosdev.yetasimplelyicsapp.data.provider.ImportedLyricsFormat
import xyz.tosdev.yetasimplelyicsapp.data.provider.ImportedLyricsProvider
import xyz.tosdev.yetasimplelyicsapp.data.provider.LrclibLyricsProvider
import xyz.tosdev.yetasimplelyicsapp.data.repository.LocalFirstLyricsRepository
import xyz.tosdev.yetasimplelyicsapp.data.repository.LyricsRepositoryResult
import xyz.tosdev.yetasimplelyicsapp.domain.lyrics.LyricsDocument
import xyz.tosdev.yetasimplelyicsapp.domain.lyrics.LyricsRequest
import xyz.tosdev.yetasimplelyicsapp.domain.lyrics.LyricsWord
import xyz.tosdev.yetasimplelyicsapp.domain.lyrics.SyncType
import xyz.tosdev.yetasimplelyicsapp.domain.lyrics.hasReliableWordSync
import xyz.tosdev.yetasimplelyicsapp.domain.lyrics.parser.LyricsDocumentExporter
import xyz.tosdev.yetasimplelyicsapp.domain.lyrics.parser.LyricsExportFormat
import xyz.tosdev.yetasimplelyicsapp.domain.playback.MonotonicClock
import xyz.tosdev.yetasimplelyicsapp.domain.playback.PlaybackStatus
import xyz.tosdev.yetasimplelyicsapp.domain.sync.LyricsSyncEngine
import xyz.tosdev.yetasimplelyicsapp.media.MediaAccessState
import xyz.tosdev.yetasimplelyicsapp.media.MediaPlaybackMonitor
import xyz.tosdev.yetasimplelyicsapp.media.TransportCommand
import xyz.tosdev.yetasimplelyicsapp.localization.AppLanguageManager
import xyz.tosdev.yetasimplelyicsapp.notification.LyricsSurfacePublisher
import xyz.tosdev.yetasimplelyicsapp.notification.shouldPublishLyricsNotification
import xyz.tosdev.yetasimplelyicsapp.overlay.LockScreenOverlayMode
import xyz.tosdev.yetasimplelyicsapp.overlay.LyricsOverlayController
import xyz.tosdev.yetasimplelyicsapp.overlay.OverlayAlignment
import xyz.tosdev.yetasimplelyicsapp.overlay.OverlayLayout
import xyz.tosdev.yetasimplelyicsapp.overlay.OverlayMode
import xyz.tosdev.yetasimplelyicsapp.overlay.OverlayPreset
import xyz.tosdev.yetasimplelyicsapp.overlay.OverlayRuntimeStatus
import xyz.tosdev.yetasimplelyicsapp.ui.AccessStatus
import xyz.tosdev.yetasimplelyicsapp.ui.CurrentWordAlignment
import xyz.tosdev.yetasimplelyicsapp.ui.EditorUiState
import xyz.tosdev.yetasimplelyicsapp.ui.LibraryItemUi
import xyz.tosdev.yetasimplelyicsapp.ui.LibraryUiState
import xyz.tosdev.yetasimplelyicsapp.ui.LyricLineUi
import xyz.tosdev.yetasimplelyicsapp.ui.LyricsAppUiState
import xyz.tosdev.yetasimplelyicsapp.ui.LyricsLoadState
import xyz.tosdev.yetasimplelyicsapp.ui.LyricsTimeline
import xyz.tosdev.yetasimplelyicsapp.ui.OverlayUiState
import xyz.tosdev.yetasimplelyicsapp.ui.SearchUiState
import xyz.tosdev.yetasimplelyicsapp.ui.SettingsUiState
import xyz.tosdev.yetasimplelyicsapp.ui.WidgetUiState
import xyz.tosdev.yetasimplelyicsapp.ui.canTogglePlayback
import xyz.tosdev.yetasimplelyicsapp.ui.presentationLines
import xyz.tosdev.yetasimplelyicsapp.widget.LyricsWidgetSnapshot
import xyz.tosdev.yetasimplelyicsapp.widget.LyricsWidgetUpdater
import xyz.tosdev.yetasimplelyicsapp.widget.WidgetUpdateDispatcher
import xyz.tosdev.yetasimplelyicsapp.domain.sync.SyncState
import xyz.tosdev.yetasimplelyicsapp.domain.sync.LyricsOffsetPolicy
import xyz.tosdev.yetasimplelyicsapp.widget.WidgetLyricsSyncType
import xyz.tosdev.yetasimplelyicsapp.widget.resolveStableWidgetWord
import xyz.tosdev.yetasimplelyicsapp.widget.toWidgetPresentationState

private const val APP_CONTAINER_TAG = "LyricsAppContainer"
private const val LYRICS_LOOKUP_TIMEOUT_MS = 30_000L

/** Owns the only playback clock; surfaces consume its output and never start clocks. */
class LyricsAppContainer(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val media = MediaPlaybackMonitor.get(context)
    val engine = LyricsSyncEngine(MonotonicClock { SystemClock.elapsedRealtime() })
    private val store = RoomLyricsStore(LyricsDatabase.getInstance(context).lyricsDao())
    private val preferences = AppPreferences(context)
    private val imported = ImportedLyricsProvider(store)
    private val remote = LrclibLyricsProvider()
    private val repository =
        LocalFirstLyricsRepository(store, listOf(imported, remote), preferences)
    private val surfaces = LyricsSurfacePublisher(context)
    private val keyguardManager = context.getSystemService(KeyguardManager::class.java)
    private val mutableState = MutableStateFlow(LyricsAppUiState())
    val state = mutableState.asStateFlow()
    private val mutableWidgetSnapshot = MutableStateFlow(LyricsWidgetSnapshot())
    val widgetSnapshot = mutableWidgetSnapshot.asStateFlow()
    val messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    private val foreground = MutableStateFlow(false)
    private val interactive =
        MutableStateFlow(context.getSystemService(PowerManager::class.java).isInteractive)
    private val timeline = LyricsTimeline()
    private val visualPreferences = VisualPreferences(context)
    private var presentedDocument: LyricsDocument? = null
    private var widgetTimedWords: List<LyricsWord> = emptyList()
    private var widgetRenderRequestedAt = -1L
    private var widgetHasReliableWordSync = false
    private var widgetSyncType = WidgetLyricsSyncType.PLAIN
    private var presentationLines: List<LyricLineUi> = emptyList()
    private var presentationById: Map<String, LyricLineUi> = emptyMap()
    private var cueBoundaries = longArrayOf()
    private var lastBoundary = Int.MIN_VALUE
    private var lastUiSecond = Long.MIN_VALUE
    private var lastLineStructure: Pair<Set<String>, Set<String>>? = null
    private var renderedLines: List<LyricLineUi> = emptyList()
    private var document: LyricsDocument? = null
    private var libraryEntries: List<LyricsLibraryItem> = emptyList()
    @StringRes private var loadMessageResource: Int? = null
    private var editorTrack: LyricsRequest? = null
    private var lookup: Job? = null
    private var offsetJob: Job? = null
    private var backgroundBoundaryJob: Job? = null
    private var currentKey: String? = null
    private var started = false
    private var notificationEnabled = false
    private var persistedArtwork: Pair<String, String>? = null
    private var pendingArtwork: Pair<String, String>? = null
    private val widgetUpdates = WidgetUpdateDispatcher(
        currentSnapshot = {
            preparePresentation()
            val now = SystemClock.elapsedRealtime()
            buildWidgetSnapshot(engine.sample(now), now)
        },
        publish = { snapshot, isCurrent ->
            try {
                LyricsWidgetUpdater.publish(context, snapshot, isCurrent)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                debugTrace(context, APP_CONTAINER_TAG) { "widget publish failed: ${error.javaClass.simpleName}" }
                false
            }
        },
        refresh = { snapshot, ids, isCurrent ->
            LyricsWidgetUpdater.refresh(context, snapshot, ids, isCurrent)
        },
        onFailure = { error ->
            debugTrace(context, APP_CONTAINER_TAG) { "widget snapshot recovery: ${error.javaClass.simpleName}" }
        },
    )
    private var lastRequestedWidgetSignature: String? = null
    private var lastDebugWordKey: String? = null

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent?) {
            interactive.value = context.getSystemService(PowerManager::class.java).isInteractive
            tick(force = true)
            scheduleNextBackgroundBoundary()
        }
    }

    fun start() {
        if (started) return
        started = true
        debugTrace(context, APP_CONTAINER_TAG) {
            "process recreated; starting playback and widget listeners"
        }
        startWidgetUpdateDispatcher()
        mutableState.update {
            it.copy(
                settings = visualPreferences.load().copy(
                    languageTag = AppLanguageManager.currentTag(context),
                ),
            )
        }
        media.startListening()

        // Initialize state from stored config
        val overlayConfig = LyricsOverlayController.loadConfig(context)
        val isOverlayEnabled = LyricsOverlayController.isPersistentEnabled(context)
        mutableState.update {
            it.copy(
                overlay = OverlayUiState(
                    enabled = isOverlayEnabled,
                    opacity = overlayConfig.opacity,
                    scale = overlayConfig.textScale,
                    mode = overlayConfig.mode.name,
                    layout = overlayConfig.layout.name,
                    currentWordAlignment = overlayConfig.currentWordAlignment.name,
                    preset = overlayConfig.preset.name,
                    alignment = overlayConfig.alignment.name,
                    clickThrough = overlayConfig.clickThrough,
                    hideWhenPaused = overlayConfig.hideWhenPaused,
                    lockScreenMode = overlayConfig.lockScreenMode.name,
                    useLockScreenNotification = overlayConfig.useLockScreenNotification,
                    background = overlayConfig.background.name,
                    cornerRadius = overlayConfig.cornerRadius,
                    paddingDp = overlayConfig.paddingDp,
                    showBorder = overlayConfig.showBorder,
                    softShadow = overlayConfig.softShadow,
                )
            )
        }

        // Restore the overlay from the visible activity or the system service restart;
        // Application.onCreate may also run in a restricted background context.

        val screenFilter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        ContextCompat.registerReceiver(
            context,
            screenReceiver,
            screenFilter,
            ContextCompat.RECEIVER_EXPORTED,
        )
        scope.launch {
            preferences.lyricsNotificationEnabled.collect {
                notificationEnabled = it
                tick(force = true)
            }
        }
        scope.launch {
            store.observeLibrary().collect { items ->
                libraryEntries = items
                renderLibrary()

            }
        }
        scope.launch {
            media.state.collect { playback ->
                engine.updatePlayback(playback.active?.playback)
                val key = playback.active?.playback?.trackKey
                val activeArtwork =
                    playback.active?.track?.takeIf { it.isArtworkCached }?.artworkUri
                if (key != null && activeArtwork != null) {
                    persistArtwork(key, activeArtwork)
                }
                if (key != currentKey) {
                    currentKey = key
                    lookup?.cancel()
                    offsetJob?.cancel()
                    document = null
                    engine.updateOffset(0)
                    engine.updateLyrics(null)
                    updateLyricsLoadState(
                        if (key == null) LyricsLoadState.IDLE else LyricsLoadState.SEARCHING,
                    )
                    if (key != null) {
                        offsetJob = launch {
                            preferences.lyricsOffsetOverride(key).collect {
                                engine.updateOffset(LyricsOffsetPolicy.resolve(document?.takeIf { it.trackKey == key }, it))
                                tick(force = true)
                                scheduleNextBackgroundBoundary()
                            }
                        }
                        request()?.let(::startLyricsLookup)
                            ?: updateLyricsLoadState(
                                LyricsLoadState.ERROR,
                                R.string.system_track_unidentified,
                            )
                    }
                }
                tick(force = true)
                scheduleNextBackgroundBoundary()
            }
        }
        scope.launch {
            combine(
                media.state,
                foreground,
                LyricsOverlayController.status,
                interactive
            ) { playback, visible, overlay, screenOn ->
                Triple(
                    playback.active?.playback?.status == PlaybackStatus.PLAYING,
                    screenOn && visible,
                    screenOn && (overlay is OverlayRuntimeStatus.Visible || overlay is OverlayRuntimeStatus.Starting)
                )
            }.distinctUntilChanged().collectLatest { (playing, visible, overlay) ->
                backgroundBoundaryJob?.cancel()
                backgroundBoundaryJob = null
                if (!playing) {
                    tick(force = true)
                    return@collectLatest
                }
                if (visible || overlay) {
                    // One Compose frame clock drives visible surfaces at the display cadence.
                    withContext(AndroidUiDispatcher.Main) {
                        while (currentCoroutineContext().isActive) {
                            withFrameNanos { tick() }
                        }
                    }
                } else {
                    // A hidden app needs updates only at semantic line/word boundaries. Schedule
                    // the exact next boundary once; callbacks reschedule after seeks/session swaps.
                    try {
                        tick(force = true)
                    } catch (error: Exception) {
                        debugTrace(context, APP_CONTAINER_TAG) {
                            "background tick failed: ${error.javaClass.simpleName}"
                        }
                    }
                    scheduleNextBackgroundBoundary()
                }
            }
        }
    }

    fun setForeground(value: Boolean) {
        foreground.value = value
        debugTrace(context, APP_CONTAINER_TAG) { "activity foreground=$value" }
        media.traceLiveness("activity foreground=$value")
        if (value) {
            media.refreshSessions()
            if (LyricsOverlayController.isPersistentEnabled(context) &&
                (LyricsOverlayController.status.value is OverlayRuntimeStatus.Stopped ||
                        LyricsOverlayController.status.value is OverlayRuntimeStatus.Error)
            ) {
                LyricsOverlayController.start(context)
            }
            tick(force = true)
        }
    }

    private fun request(): LyricsRequest? {
        val active = media.state.value.active ?: return null
        val title = active.track.title?.takeIf(String::isNotBlank) ?: return null
        return LyricsRequest(
            trackKey = active.playback.trackKey ?: return null,
            title = title,
            artists = listOf(active.track.artist.orEmpty()),
            album = active.track.album,
            durationMs = active.track.durationMs,
            artworkUri = active.track.artworkUri.takeIf { active.track.isArtworkCached },
        )
    }

    private suspend fun setDocument(value: LyricsDocument) {
        val savedOffset = preferences.lyricsOffsetOverride(value.trackKey).first()
        if (currentKey != null && currentKey != value.trackKey) return
        document = value
        engine.updateOffset(LyricsOffsetPolicy.resolve(value, savedOffset))
        engine.updateLyrics(value)
        updateLyricsLoadState(LyricsLoadState.AVAILABLE)
        debugTrace(context, APP_CONTAINER_TAG) {
            "lyrics resolved track=${value.trackKey} sync=${value.syncType} lines=${value.lines.size}"
        }
        media.state.value.active?.track
            ?.takeIf { it.isArtworkCached }
            ?.artworkUri
            ?.let { persistArtwork(value.trackKey, it) }
        tick(force = true)
        scheduleNextBackgroundBoundary()
    }

    private fun startLyricsLookup(request: LyricsRequest, forceRefresh: Boolean = false) {
        lookup?.cancel()
        updateLyricsLoadState(LyricsLoadState.SEARCHING)
        lookup = scope.launch {
            val result = withTimeoutOrNull(LYRICS_LOOKUP_TIMEOUT_MS) {
                repository.findLyrics(request, forceRefresh = forceRefresh)
            }
            if (currentKey != request.trackKey) return@launch
            when (result) {
                is LyricsRepositoryResult.Found -> setDocument(result.document)
                LyricsRepositoryResult.NotFound -> updateLyricsLoadState(LyricsLoadState.NOT_FOUND)
                is LyricsRepositoryResult.RateLimited -> updateLyricsLoadState(
                    LyricsLoadState.ERROR,
                    R.string.system_lyrics_rate_limited,
                )
                is LyricsRepositoryResult.Unavailable -> updateLyricsLoadState(
                    LyricsLoadState.ERROR,
                    R.string.system_lyrics_unavailable,
                )
                is LyricsRepositoryResult.Failed -> updateLyricsLoadState(
                    LyricsLoadState.ERROR,
                    R.string.system_lyrics_failed,
                )
                null -> updateLyricsLoadState(
                    LyricsLoadState.ERROR,
                    R.string.system_lyrics_timeout,
                )
            }
        }
    }

    private fun updateLyricsLoadState(state: LyricsLoadState, @StringRes message: Int? = null) {
        loadMessageResource = message
        mutableState.update { current ->
            current.copy(
                nowPlaying = current.nowPlaying.copy(
                    lyricsLoadState = state,
                    lyricsLoadMessage = message?.let { localizedString(it) },
                ),
            )
        }
    }

    fun retryLyrics() {
        val request = request()
        if (request == null) {
            updateLyricsLoadState(
                LyricsLoadState.ERROR,
                R.string.system_track_unidentified,
            )
            return
        }
        document = null
        engine.updateLyrics(null)
        startLyricsLookup(request, forceRefresh = true)
        tick(force = true)
    }

    private fun persistArtwork(trackKey: String, artworkUri: String) {
        val candidate = trackKey to artworkUri
        if (persistedArtwork == candidate || pendingArtwork == candidate) return
        pendingArtwork = candidate
        scope.launch(Dispatchers.IO) {
            val saved = store.updateArtwork(trackKey, artworkUri)
            withContext(Dispatchers.Main.immediate) {
                if (pendingArtwork == candidate) pendingArtwork = null
                if (saved) persistedArtwork = candidate
            }
        }
    }

    private fun preparePresentation() {
        if (presentedDocument === document) return
        presentedDocument = document
        widgetHasReliableWordSync = document?.hasReliableWordSync == true
        widgetSyncType = when {
            widgetHasReliableWordSync -> WidgetLyricsSyncType.WORD
            document?.lines?.any { it.startTimeMs != null } == true -> WidgetLyricsSyncType.LINE
            else -> WidgetLyricsSyncType.PLAIN
        }
        presentationLines = document?.presentationLines().orEmpty()
        presentationById = presentationLines.associateBy { it.id }
        widgetTimedWords = document?.lines.orEmpty()
            .asSequence()
            .flatMap { it.words.asSequence() }
            .filter { it.startTimeMs != null && it.text.isNotBlank() }
            .sortedBy { it.startTimeMs }
            .toList()
        cueBoundaries = document?.lines.orEmpty().flatMap { line ->
            listOfNotNull(line.startTimeMs, line.endTimeMs) +
                    line.words.flatMap { listOfNotNull(it.startTimeMs, it.endTimeMs) }
        }.distinct().sorted().toLongArray()
        lastBoundary = Int.MIN_VALUE
        lastLineStructure = null
    }

    private fun tick(force: Boolean = false) {
        preparePresentation()
        val now = SystemClock.elapsedRealtime()
        val playback = media.state.value.active?.playback
        val position = playback?.estimatedPositionAt(now)
        val lyricsPosition = position?.minus(engine.state.value.offsetMs)?.coerceAtLeast(0L)
        timeline.positionMs = lyricsPosition
        val found = lyricsPosition?.let { cueBoundaries.binarySearch(it) } ?: Int.MIN_VALUE
        val boundary =
            if (found >= 0) found + 1 else if (found == Int.MIN_VALUE) found else -found - 1
        val second = (position ?: -1000L) / 1000L
        if (!force && boundary == lastBoundary && second == lastUiSecond) return
        lastBoundary = boundary
        lastUiSecond = second
        val sync = engine.sample(now)
        val activeIds = sync.activeLines.map { it.line.id }.toSet()
        val pastIds = presentationLines.filter {
            it.endTimeMs != null && lyricsPosition != null && it.endTimeMs <= lyricsPosition
        }.map { it.id }.toSet()
        val structure = activeIds to pastIds
        if (structure != lastLineStructure) {
            lastLineStructure = structure
            renderedLines = presentationLines.map {
                it.copy(isActive = it.id in activeIds, isPast = it.id in pastIds)
            }
        }
        render()
        publish()
    }

    private fun scheduleNextBackgroundBoundary() {
        backgroundBoundaryJob?.cancel()
        backgroundBoundaryJob = null
        val active = media.state.value.active ?: return
        val playback = active.playback
        if (playback.status != PlaybackStatus.PLAYING) return
        val overlayStatus = LyricsOverlayController.status.value
        val hasVisibleSurface = interactive.value && (
            foreground.value ||
                overlayStatus is OverlayRuntimeStatus.Visible ||
                overlayStatus is OverlayRuntimeStatus.Starting
            )
        if (hasVisibleSurface) return

        preparePresentation()
        val now = SystemClock.elapsedRealtime()
        val playerPosition = playback.estimatedPositionAt(now) ?: return
        val lyricsPosition = (playerPosition - engine.state.value.offsetMs).coerceAtLeast(0L)
        val delayMs = nextCueBoundaryDelayMs(
            boundaries = cueBoundaries,
            lyricsPositionMs = lyricsPosition,
            playbackSpeed = playback.speed,
        ) ?: return
        backgroundBoundaryJob = scope.launch {
            val thisJob = currentCoroutineContext()[Job]
            debugTrace(context, APP_CONTAINER_TAG) {
                "background boundary scheduled targetElapsed=${now + delayMs} delayMs=$delayMs positionMs=$lyricsPosition sampleAt=${playback.reportedAtElapsedMs}"
            }
            try {
                delay(delayMs)
                debugTrace(context, APP_CONTAINER_TAG) { "background boundary fired actualElapsed=${SystemClock.elapsedRealtime()} lateMs=${SystemClock.elapsedRealtime() - now - delayMs}" }
                tick(force = true)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                // One bad publication must not permanently kill the only background clock.
                debugTrace(context, APP_CONTAINER_TAG) {
                    "background boundary failed: ${error.javaClass.simpleName}"
                }
            } finally {
                if (backgroundBoundaryJob === thisJob) {
                    backgroundBoundaryJob = null
                    if (currentCoroutineContext().isActive) {
                        scheduleNextBackgroundBoundary()
                    }
                }
            }
        }
    }

    private fun buildWidgetSnapshot(sync: SyncState, now: Long): LyricsWidgetSnapshot {
        val active = media.state.value.active
        val displayedWord = resolveStableWidgetWord(
            currentWord = sync.activeWord,
            lyricsPositionMs = sync.lyricsPositionMs,
            timedWords = widgetTimedWords,
        )
        val nextWord = nextSemanticWidgetWord(widgetTimedWords, sync.lyricsPositionMs)
        val playback = active?.playback
        val offset = sync.offsetMs
        return LyricsWidgetSnapshot(
            trackKey = active?.takeIf { !it.track.title.isNullOrBlank() || !it.track.mediaId.isNullOrBlank() }
                ?.playback?.trackKey.orEmpty(), title = active?.track?.title.orEmpty(),
            artist = active?.track?.artist.orEmpty(),
            previousLineId = sync.previousLine?.id.orEmpty(),
            previousLine = sync.previousLine?.text?.original.orEmpty(),
            activeLineId = sync.activeLine?.id.orEmpty(),
            activeLine = sync.activeLine?.text?.original.orEmpty(),
            nextLineId = sync.nextLine?.id.orEmpty(),
            nextLine = sync.nextLine?.text?.original.orEmpty(),
            translation = sync.activeLine?.text?.translations?.values?.firstOrNull().orEmpty(),
            activeWordId = displayedWord?.id.orEmpty(),
            activeWord = displayedWord?.text.orEmpty(),
            activeWordSourceStartMs = displayedWord?.startTimeMs ?: -1,
            activeWordScheduledAtElapsedMs = wordDeadlineElapsedMs(playback, displayedWord?.startTimeMs, offset),
            renderRequestedAtElapsedMs = widgetRenderRequestedAt,
            nextWordId = nextWord?.id.orEmpty(),
            nextWord = nextWord?.text.orEmpty(),
            nextWordSourceStartMs = nextWord?.startTimeMs ?: -1,
            nextWordScheduledAtElapsedMs = wordDeadlineElapsedMs(playback, nextWord?.startTimeMs, offset),
            hasReliableWordSync = widgetHasReliableWordSync,
            syncType = widgetSyncType,
            lyricsLanguageTag = document?.languageTag.orEmpty(),
            isPlaying = active?.playback?.status == PlaybackStatus.PLAYING,
            presentationState = mutableState.value.nowPlaying.toWidgetPresentationState(),
            capturedAtElapsedMs = now,
        )
    }

    /** Read on Main. Used by configuration/resize events even while playback is paused. */
    internal fun currentWidgetSnapshot(): LyricsWidgetSnapshot {
        preparePresentation()
        val now = SystemClock.elapsedRealtime()
        return buildWidgetSnapshot(engine.sample(now), now)
    }

    private fun publish() {
        val sync = engine.state.value
        val snapshot = buildWidgetSnapshot(sync, SystemClock.elapsedRealtime())
        // Each widget resolves AUTO/WORD/LINE independently. The conflated dispatcher accepts
        // only semantic snapshot changes, so frame ticks cannot queue unbounded Glance work.
        if (mutableWidgetSnapshot.value.storedSnapshotSignature() != snapshot.storedSnapshotSignature()) {
            mutableWidgetSnapshot.value = snapshot
            mutableState.update { it.copy(widgetSnapshot = snapshot) }
        }
        requestWidgetUpdate(snapshot)

        // Notification/shared-surface failures are isolated from widgets. The widget snapshot is
        // already queued before this call, so a failing optional surface cannot freeze it.
        try {
            val publishNotification = shouldPublishLyricsNotification(
                alwaysEnabled = notificationEnabled,
                lockScreenFallbackEnabled = mutableState.value.overlay.useLockScreenNotification,
                isKeyguardLocked = keyguardManager.isKeyguardLocked,
            )
            surfaces.publish(
                sync,
                media.state.value,
                publishNotification,
                presentationById,
                timeline,
                mutableState.value.settings,
            )
        } catch (error: Exception) {
            debugTrace(context, APP_CONTAINER_TAG) {
                "surface publish failed: ${error.javaClass.simpleName}"
            }
        }
    }

    private fun startWidgetUpdateDispatcher() {
        scope.launch { widgetUpdates.run() }
    }

    internal fun requestWidgetRefresh(appWidgetIds: Set<Int>) {
        widgetUpdates.requestRefresh(appWidgetIds)
    }

    private fun requestWidgetUpdate(snapshot: LyricsWidgetSnapshot) {
        val signature = snapshot.storedSnapshotSignature()
        if (signature == lastRequestedWidgetSignature) return
        lastRequestedWidgetSignature = signature
        widgetRenderRequestedAt = SystemClock.elapsedRealtime()

        val wordKey = "${snapshot.trackKey}|${snapshot.activeWordId}"
        if (wordKey != lastDebugWordKey) {
            lastDebugWordKey = wordKey
            debugTrace(context, APP_CONTAINER_TAG) {
                "current word changed track=${snapshot.trackKey.hashCode()} word=${snapshot.activeWordId.hashCode()} sampleAt=${snapshot.capturedAtElapsedMs}"
            }
        }
        debugTrace(context, APP_CONTAINER_TAG) {
            "widget update requested signature=${signature.hashCode()} " +
                "requestAt=${SystemClock.elapsedRealtime()} sampleAt=${snapshot.capturedAtElapsedMs}"
        }
        widgetUpdates.request()
    }

    private fun render() {
        val active = media.state.value.active
        val sync = engine.state.value
        mutableState.update { old ->
            old.copy(
                nowPlaying = old.nowPlaying.copy(
                    connectionLabel = when (media.state.value.access) {
                        MediaAccessState.PERMISSION_REQUIRED -> localizedString(R.string.system_media_permission_required)
                        MediaAccessState.CONNECTING -> localizedString(R.string.system_media_connecting)
                        MediaAccessState.NO_ACTIVE_SESSION -> localizedString(R.string.system_media_no_session)
                        MediaAccessState.CONNECTED -> active?.packageName.orEmpty()
                    },
                    title = active?.track?.title,
                    artist = active?.track?.artist,
                    album = active?.track?.album,
                    artworkUri = active?.track?.artworkUri,
                    positionMs = active?.playback?.estimatedPositionAt(SystemClock.elapsedRealtime())
                        ?: 0L,
                    durationMs = active?.track?.durationMs ?: 0L,
                    hasActiveSession = active != null,
                    isPlaying = active?.playback?.status == PlaybackStatus.PLAYING,
                    canTogglePlayback = canTogglePlayback(
                        isPlaying = active?.playback?.status == PlaybackStatus.PLAYING,
                        canPlay = active?.capabilities?.canPlay == true,
                        canPause = active?.capabilities?.canPause == true,
                    ),
                    canSkipPrevious = active?.capabilities?.canSkipPrevious == true,
                    canSkipNext = active?.capabilities?.canSkipNext == true,
                    sourceLabel = document?.source?.providerId,
                    activeLineId = sync.activeLine?.id,
                    activeWordId = sync.activeWord?.id,
                    offsetMs = sync.offsetMs,
                    lyrics = renderedLines,
                    timeline = timeline,
                    canSeek = active?.playback?.isSeekable == true,
                ),
                systemAccess = old.systemAccess.copy(
                    mediaSession = if (media.hasNotificationAccess()) AccessStatus.GRANTED else AccessStatus.NOT_REQUESTED,
                    overlay = if (LyricsOverlayController.canDrawOverlays(context)) AccessStatus.GRANTED else AccessStatus.NOT_REQUESTED,
                    notifications = if (context.getSystemService(NotificationManager::class.java)
                            .areNotificationsEnabled()
                    ) AccessStatus.GRANTED else AccessStatus.DENIED,
                    lyricsNotificationEnabled = notificationEnabled
                ),
                overlay = old.overlay.copy(
                    enabled = LyricsOverlayController.status.value !is OverlayRuntimeStatus.Stopped
                )
            )
        }
    }

    fun togglePlayback() {
        media.dispatch(TransportCommand.PLAY_PAUSE)
    }

    fun skipPrevious() {
        media.dispatch(TransportCommand.PREVIOUS)
    }

    fun skipNext() {
        media.dispatch(TransportCommand.NEXT)
    }

    fun seek(fraction: Float) {
        media.state.value.active?.track?.durationMs?.let {
            media.seekTo(
                (it * fraction.coerceIn(
                    0f,
                    1f
                )).toLong()
            )
        }
    }

    fun offset(value: Long) {
        currentKey?.let { key -> scope.launch { preferences.setLyricsOffset(key, value) } }
    }

    fun settings(value: SettingsUiState) {
        visualPreferences.save(value)
        mutableState.update { it.copy(settings = value) }
        publish()
    }

    private fun renderLibrary() {
        val items = libraryEntries
                fun LyricsLibraryItem.asUi() = LibraryItemUi(
                    id = documentId,
                    title = metadata.title ?: localizedString(R.string.system_imported_lyrics),
                    artist = metadata.artists.joinToString(", "),
                    detail = if (sourceId == "local") {
                        localizedString(R.string.system_imported_offline)
                    } else {
                        localizedString(R.string.system_saved_provider, sourceId)
                    },
                    hasWordSync = syncType == SyncType.WORD || syncType == SyncType.MIXED,
                    artworkUri = metadata.artworkUri,
                )
                mutableState.update {
                    it.copy(
                        library = LibraryUiState(
                            recent = items.map { item -> item.asUi() },
                            favorites = items.filter { item -> item.isPinned }
                                .map { item -> item.asUi() },
                        )
                    )
                }
    }

    fun refreshLocalizedSurfaces() {
        mutableState.update { current ->
            current.copy(settings = current.settings.copy(languageTag = AppLanguageManager.currentTag(context)),
                nowPlaying = current.nowPlaying.copy(lyricsLoadMessage = loadMessageResource?.let { localizedString(it) }),
                search = current.search.copy(message = current.search.message?.let { localizedString(R.string.system_searching) }))
        }
        render()
        renderLibrary()
        surfaces.refreshLocalization()
        LyricsOverlayController.refreshLocalization(context)
        scope.launch { requestWidgetRefresh(LyricsWidgetUpdater.installedIds(context).toSet()) }
        publish()
    }

    fun seekLine(id: String) {
        if (media.state.value.active?.playback?.isSeekable != true) return
        document?.lines?.firstOrNull { it.id == id }?.startTimeMs?.let {
            media.seekTo((it + engine.state.value.offsetMs).coerceAtLeast(0L))
        }
    }

    fun overlay(value: OverlayUiState) {
        val config = LyricsOverlayController.loadConfig(context).copy(
            opacity = value.opacity,
            textScale = value.scale,
            mode = OverlayMode.valueOf(value.mode),
            layout = runCatching { OverlayLayout.valueOf(value.layout) }
                .getOrDefault(OverlayLayout.CURRENT_LINE),
            currentWordAlignment = runCatching {
                CurrentWordAlignment.valueOf(value.currentWordAlignment)
            }.getOrDefault(CurrentWordAlignment.CENTER),
            preset = OverlayPreset.valueOf(value.preset),
            alignment = OverlayAlignment.valueOf(value.alignment),
            clickThrough = value.clickThrough,
            hideWhenPaused = value.hideWhenPaused,
            lockScreenMode = runCatching {
                LockScreenOverlayMode.valueOf(value.lockScreenMode)
            }.getOrDefault(LockScreenOverlayMode.OFF),
            useLockScreenNotification = value.useLockScreenNotification,
            background = xyz.tosdev.yetasimplelyicsapp.overlay.OverlayBackground.valueOf(value.background),
            cornerRadius = value.cornerRadius,
            paddingDp = value.paddingDp,
            showBorder = value.showBorder,
            softShadow = value.softShadow,
        )
        LyricsOverlayController.updateConfig(context, config)
        if (value.enabled) LyricsOverlayController.start(
            context,
            config
        ) else LyricsOverlayController.stop(context)
        mutableState.update {
            it.copy(
                overlay = value.copy(
                    enabled = value.enabled && LyricsOverlayController.canDrawOverlays(
                        context
                    )
                )
            )
        }
    }

    fun widget(value: WidgetUiState) {
        mutableState.update { it.copy(widget = value) }
    }

    fun notification(value: Boolean) {
        notificationEnabled = value
        scope.launch { preferences.setLyricsNotificationEnabled(value) }
        render()
        publish()
    }

    fun importDocuments(uris: List<Uri>) {
        if (uris.size > 20) {
            messages.tryEmit(localizedString(R.string.system_import_limit))
            return
        }
        // A batch is a library import; only a single selection binds to the current song.
        val target = if (uris.size == 1) request() else null
        scope.launch {
            if (target != null) lookup?.cancelAndJoin()
            var importedCount = 0
            for (uri in uris) {
                try {
                    val (filename, text) = withContext(Dispatchers.IO) {
                        val name = context.contentResolver.query(
                            uri,
                            arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null
                        )?.use { cursor ->
                            if (cursor.moveToFirst()) cursor.getString(0) else null
                        } ?: localizedString(R.string.system_default_import_filename)
                        val content = context.contentResolver.openInputStream(uri)
                            ?.use(LyricsImportReader::read)
                            ?: error(localizedString(R.string.system_file_open_failed))
                        name to content
                    }
                    importContent(text, filename, target)
                    importedCount++
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    messages.emit(
                        localizedString(
                            R.string.system_import_failed,
                            localizedString(if (error is LyricsImportException) when (error.reason) {
                                LyricsImportFailure.TOO_LARGE -> R.string.system_import_too_large
                                LyricsImportFailure.EMPTY -> R.string.system_import_empty
                                LyricsImportFailure.ENCODING -> R.string.system_import_encoding
                            } else R.string.system_invalid_file),
                        ),
                    )
                }
            }
            if (importedCount > 0) messages.emit(
                if (target != null) localizedString(R.string.system_saved_for_track, target.title)
                else localizedQuantity(R.plurals.system_saved_to_library, importedCount, importedCount)
            )
        }
    }

    private suspend fun importContent(text: String, filename: String, target: LyricsRequest?) {
        val extension = filename.substringAfterLast('.', "").lowercase()
        val isXml = text.trimStart().startsWith("<")
        require(extension in setOf("ttml", "xml", "lrc", "txt") || isXml) {
            localizedString(R.string.system_supported_file_required)
        }
        val current = target ?: LyricsRequest(
            "import:" + java.util.UUID.randomUUID(),
            filename.substringBeforeLast('.').ifBlank {
                localizedString(R.string.system_imported_lyrics)
            }, listOf("")
        )
        val format = if (extension == "ttml" || extension == "xml" || isXml)
            ImportedLyricsFormat.TTML else ImportedLyricsFormat.LRC
        val result = withContext(Dispatchers.IO) { imported.import(current, text, format) }
        if (currentKey == null || currentKey == current.trackKey) setDocument(result)
        editorTrack = current
        mutableState.update {
            it.copy(
                editor = EditorUiState(
                    current.title, current.artists.joinToString(), text
                )
            )
        }
    }

    fun importText(text: String, filename: String) {
        val target = request()
        scope.launch {
            try {
                if (target != null) lookup?.cancelAndJoin()
                importContent(text, filename, target)
                messages.emit(localizedString(R.string.system_import_saved_offline))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                messages.emit(localizedString(R.string.system_import_lyrics_failed))
            }
        }
    }

    fun saveEditor(editor: EditorUiState) {
        val target = editorTrack ?: request()
        scope.launch {
            try {
                importContent(
                    editor.rawLyrics,
                    editor.title + if (editor.rawLyrics.trimStart()
                            .startsWith("<")
                    ) ".ttml" else ".lrc",
                    target
                )
                target?.let { preferences.setLyricsOffset(it.trackKey, editor.offsetMs) }
                messages.emit(localizedString(R.string.system_lyrics_saved))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                messages.emit(localizedString(R.string.system_save_failed))
            }
        }
    }

    fun exportText(): String = document?.lines?.joinToString("\n") { it.text.original }.orEmpty()
    fun exportTtml(): String? {
        val doc = document ?: return null
        return LyricsDocumentExporter().export(doc, LyricsExportFormat.TTML).content
    }

    fun search(query: String) {
        mutableState.update {
            it.copy(
                search = SearchUiState(
                    query = query,
                    message = localizedString(R.string.system_searching)
                )
            )
        }
    }

    fun openLibrary(id: String) {
        scope.launch {
            try {
                val cached = store.findById(id) ?: return@launch
                store.markAccessed(id, System.currentTimeMillis())
                editorTrack = LyricsRequest(
                    cached.document.trackKey,
                    cached.metadata.title ?: localizedString(R.string.system_imported_lyrics),
                    cached.metadata.artists.ifEmpty { listOf("") },
                    cached.metadata.album, cached.metadata.durationMs,
                    artworkUri = cached.metadata.artworkUri
                )
                val content = withContext(Dispatchers.Default) {
                    LyricsDocumentExporter().export(
                        cached.document,
                        LyricsExportFormat.TTML
                    ).content
                }
                val savedOffset = LyricsOffsetPolicy.resolve(cached.document,
                    preferences.lyricsOffsetOverride(cached.document.trackKey).first())
                mutableState.update {
                    it.copy(
                        editor = EditorUiState(
                            cached.metadata.title ?: localizedString(R.string.system_imported_lyrics),
                            cached.metadata.artists.joinToString(),
                            content,
                            savedOffset
                        )
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                messages.emit(localizedString(R.string.system_open_lyrics_failed))
            }
        }
    }

    private fun localizedString(@StringRes id: Int, vararg args: Any): String =
        AppLanguageManager.localizedContext(context).getString(id, *args)

    private fun localizedQuantity(@PluralsRes id: Int, quantity: Int, vararg args: Any): String =
        AppLanguageManager.localizedContext(context).resources
            .getQuantityString(id, quantity, *args)
}
