package xyz.tosdev.yetasimplelyicsapp.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.MusicOff
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds
import xyz.tosdev.yetasimplelyicsapp.R
import xyz.tosdev.yetasimplelyicsapp.ui.LyricsAppActions
import xyz.tosdev.yetasimplelyicsapp.ui.LyricsDisplayMode
import xyz.tosdev.yetasimplelyicsapp.ui.LyricsLoadState
import xyz.tosdev.yetasimplelyicsapp.ui.MotionLevel
import xyz.tosdev.yetasimplelyicsapp.ui.NowPlayingUiState
import xyz.tosdev.yetasimplelyicsapp.ui.SettingsUiState
import xyz.tosdev.yetasimplelyicsapp.ui.scaledLyricsSp
import xyz.tosdev.yetasimplelyicsapp.ui.components.AutoScrollingLyrics
import xyz.tosdev.yetasimplelyicsapp.ui.components.CurrentWordLyrics
import xyz.tosdev.yetasimplelyicsapp.ui.components.SongArtwork
import xyz.tosdev.yetasimplelyicsapp.ui.designsystem.SignalDesignSystem
import xyz.tosdev.yetasimplelyicsapp.ui.designsystem.SignalIconButton
import xyz.tosdev.yetasimplelyicsapp.ui.designsystem.SignalMotionScheme
import xyz.tosdev.yetasimplelyicsapp.ui.designsystem.SignalMotionSpecs
import xyz.tosdev.yetasimplelyicsapp.ui.designsystem.rememberSignalMotion

@Composable
fun NowPlayingScreen(
    state: NowPlayingUiState,
    settings: SettingsUiState,
    actions: LyricsAppActions,
    fullscreen: Boolean = false,
    onFullscreenChange: (Boolean) -> Unit = {},
) {
    val latestState by rememberUpdatedState(state)
    val latestSettings by rememberUpdatedState(settings)
    val latestActions by rememberUpdatedState(actions)
    val latestFullscreen by rememberUpdatedState(fullscreen)
    val latestFullscreenChange by rememberUpdatedState(onFullscreenChange)
    val lyricsExperience = remember {
        movableContentOf<Modifier> { paneModifier ->
            Box(paneModifier) {
                LyricsPane(
                    state = latestState,
                    settings = latestSettings,
                    actions = latestActions,
                    modifier = Modifier.fillMaxSize(),
                )
                SignalIconButton(
                    icon = if (latestFullscreen) Icons.Rounded.FullscreenExit else Icons.Rounded.Fullscreen,
                    contentDescription = stringResource(
                        if (latestFullscreen) R.string.ux_immersive_exit_lyrics
                        else R.string.ux_immersive_enter_lyrics,
                    ),
                    onClick = { latestFullscreenChange(!latestFullscreen) },
                    motionLevel = latestSettings.motionLevel,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp),
                )
            }
        }
    }
    val motion = rememberSignalMotion(settings.motionLevel)
    BoxWithConstraints(
        modifier = Modifier.fillMaxSize(),
    ) {
        val wide = maxWidth >= 760.dp
        val shortLandscape = maxHeight < 620.dp

        if (wide) {
            val wideGap by animateDpAsState(
                targetValue = if (fullscreen) 0.dp else SignalDesignSystem.spacing.xLarge,
                animationSpec = SignalMotionSpecs.immersive(motion),
                label = "fullscreen lyrics gap",
            )
            val summaryWidth =
                (maxWidth - SignalDesignSystem.spacing.xLarge).coerceAtLeast(0.dp) * 0.38f
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(wideGap),
            ) {
                AnimatedVisibility(
                    visible = !fullscreen,
                    modifier = Modifier.fillMaxHeight(),
                    enter = nowPlayingChromeEnter(motion, horizontal = true),
                    exit = nowPlayingChromeExit(motion, horizontal = true),
                ) {
                    Column(
                        Modifier
                            .width(summaryWidth)
                            .fillMaxHeight(),
                    ) {
                        PlaybackSummary(
                            state = state,
                            motionLevel = settings.motionLevel,
                            compact = shortLandscape,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.weight(1f))
                        PlaybackControls(
                            state = state,
                            actions = actions,
                            motionLevel = settings.motionLevel,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                lyricsExperience(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                )
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                AnimatedVisibility(
                    visible = !fullscreen,
                    enter = nowPlayingChromeEnter(motion, horizontal = false),
                    exit = nowPlayingChromeExit(motion, horizontal = false),
                ) {
                    Column {
                        PlaybackSummary(
                            state = state,
                            motionLevel = settings.motionLevel,
                            compact = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(SignalDesignSystem.spacing.small))
                    }
                }
                lyricsExperience(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f),
                )
                AnimatedVisibility(
                    visible = !fullscreen,
                    enter = nowPlayingChromeEnter(motion, horizontal = false),
                    exit = nowPlayingChromeExit(motion, horizontal = false),
                ) {
                    PlaybackControls(
                        state = state,
                        actions = actions,
                        motionLevel = settings.motionLevel,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

private fun nowPlayingChromeEnter(
    motion: SignalMotionScheme,
    horizontal: Boolean,
): EnterTransition = when {
    !motion.enabled -> EnterTransition.None
    motion.reduced -> fadeIn(SignalMotionSpecs.content(motion))
    horizontal -> fadeIn(SignalMotionSpecs.immersive(motion)) +
        expandHorizontally(animationSpec = SignalMotionSpecs.immersive(motion))
    else -> fadeIn(SignalMotionSpecs.immersive(motion)) +
        expandVertically(animationSpec = SignalMotionSpecs.immersive(motion))
}

private fun nowPlayingChromeExit(
    motion: SignalMotionScheme,
    horizontal: Boolean,
): ExitTransition = when {
    !motion.enabled -> ExitTransition.None
    motion.reduced -> fadeOut(SignalMotionSpecs.exit(motion))
    horizontal -> fadeOut(SignalMotionSpecs.exit(motion)) +
        shrinkHorizontally(animationSpec = SignalMotionSpecs.immersive(motion))
    else -> fadeOut(SignalMotionSpecs.exit(motion)) +
        shrinkVertically(animationSpec = SignalMotionSpecs.immersive(motion))
}

@Composable
private fun PlaybackSummary(
    state: NowPlayingUiState,
    motionLevel: MotionLevel,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val motion = rememberSignalMotion(motionLevel)
    AnimatedContent(
        targetState = TrackVisual.from(state),
        transitionSpec = { trackChangeTransform(motion) },
        label = "track identity",
        modifier = modifier,
    ) { track ->
        if (compact) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Artwork(
                        artworkUri = track.artworkUri,
                        title = track.title,
                        hasTrack = track.hasTrack,
                        motionLevel = motionLevel,
                        modifier = Modifier
                            .size(88.dp)
                            .aspectRatio(1f),
                    )
                    TrackMetadata(
                        track = track,
                        compact = true,
                        motionLevel = motionLevel,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        } else {
            Column {
                Artwork(
                    artworkUri = track.artworkUri,
                    title = track.title,
                    hasTrack = track.hasTrack,
                    motionLevel = motionLevel,
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 252.dp)
                        .aspectRatio(1f),
                )
                Spacer(Modifier.height(16.dp))
                TrackMetadata(track = track, motionLevel = motionLevel)
            }
        }
    }
}

@Composable
private fun TrackMetadata(
    track: TrackVisual,
    motionLevel: MotionLevel,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    Column(modifier) {
        MeasuredMarqueeText(
            text = track.title ?: stringResource(R.string.ui_now_playing_no_song),
            marqueeKey = track,
            style = if (compact) {
                MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Bold,
                    lineHeight = 26.sp,
                )
            } else {
                MaterialTheme.typography.headlineMedium
            },
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = if (compact) 1 else 2,
            motionLevel = motionLevel,
        )
        Spacer(Modifier.height(if (compact) 2.dp else 5.dp))
        MeasuredMarqueeText(
            text = track.artist ?: stringResource(R.string.ui_now_playing_start_player),
            marqueeKey = track,
            style = if (compact) {
                MaterialTheme.typography.bodyLarge
            } else {
                MaterialTheme.typography.bodyLarge
            },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = if (compact) 1 else 2,
            motionLevel = motionLevel,
        )
        track.album?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.60f),
                maxLines = 1,
            )
        }
        track.provider?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.42f),
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun MeasuredMarqueeText(
    text: String,
    marqueeKey: Any,
    style: TextStyle,
    color: Color,
    maxLines: Int,
    motionLevel: MotionLevel,
    modifier: Modifier = Modifier,
) {
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val motion = rememberSignalMotion(motionLevel)
    val marqueeContentDescription = stringResource(R.string.ux_marquee_full_text, text)
    var viewportWidthPx by remember { mutableIntStateOf(0) }
    val naturalLayout = remember(text, style, textMeasurer, density) {
        textMeasurer.measure(
            text = text,
            style = style,
            softWrap = false,
            maxLines = 1,
        )
    }
    val boundedLayout = remember(text, style, maxLines, viewportWidthPx, textMeasurer, density) {
        if (viewportWidthPx <= 0) null else textMeasurer.measure(
            text = text,
            style = style,
            overflow = TextOverflow.Clip,
            softWrap = true,
            maxLines = maxLines,
            constraints = Constraints(maxWidth = viewportWidthPx),
        )
    }
    val hasOverflow = boundedLayout?.hasVisualOverflow == true
    val travelPx = (naturalLayout.size.width - viewportWidthPx).coerceAtLeast(0).toFloat()
    val animateMarquee = shouldAnimateMetadataMarquee(
        hasOverflow = hasOverflow && travelPx > 0f,
        motionEnabled = motion.enabled,
        motionReduced = motion.reduced,
    )
    val offset = remember(marqueeKey) { Animatable(0f) }
    val resetAlpha = remember(marqueeKey) { Animatable(1f) }
    LaunchedEffect(marqueeKey, travelPx, animateMarquee) {
        offset.snapTo(0f)
        resetAlpha.snapTo(1f)
        if (!animateMarquee) return@LaunchedEffect
        val durationMillis = marqueeTravelDurationMillis(travelPx / density.density)
        val easing = CubicBezierEasing(0.42f, 0f, 0.58f, 1f)
        while (true) {
            delay(MARQUEE_EDGE_PAUSE_MILLIS.milliseconds)
            offset.animateTo(
                targetValue = travelPx,
                animationSpec = tween(durationMillis = durationMillis, easing = easing),
            )
            delay(MARQUEE_EDGE_PAUSE_MILLIS.milliseconds)
            resetAlpha.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = MARQUEE_RESET_FADE_MILLIS),
            )
            offset.snapTo(0f)
            resetAlpha.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = MARQUEE_RESET_FADE_MILLIS),
            )
        }
    }

    val measuredHeight = boundedLayout?.size?.height
    Box(
        modifier = modifier
            .fillMaxWidth()
            .onSizeChanged { viewportWidthPx = it.width }
            .then(
                if (hasOverflow && measuredHeight != null) {
                    Modifier.height(with(density) { measuredHeight.toDp() })
                } else Modifier
            )
            .clipToBounds()
            .then(
                if (hasOverflow) Modifier.clearAndSetSemantics {
                    contentDescription = marqueeContentDescription
                }
                else Modifier
            ),
    ) {
        if (hasOverflow) {
            Text(
                text = text,
                style = style,
                color = color,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip,
                modifier = Modifier
                    .wrapContentWidth(align = Alignment.Start, unbounded = true)
                    .width(with(density) { naturalLayout.size.width.toDp() })
                    .graphicsLayer {
                        translationX = -offset.value
                        alpha = resetAlpha.value
                    }
                    .clearAndSetSemantics {},
            )
        } else {
            Text(
                text = text,
                style = style,
                color = color,
                maxLines = maxLines,
                overflow = TextOverflow.Clip,
            )
        }
    }
}

private const val MARQUEE_EDGE_PAUSE_MILLIS = 1_250L
private const val MARQUEE_RESET_FADE_MILLIS = 240

internal fun shouldAnimateMetadataMarquee(
    hasOverflow: Boolean,
    motionEnabled: Boolean,
    motionReduced: Boolean,
): Boolean = hasOverflow && motionEnabled && !motionReduced

internal fun marqueeTravelDurationMillis(distanceDp: Float): Int =
    ((distanceDp.coerceAtLeast(0f) / 22f) * 1_000f)
        .toInt()
        .coerceIn(4_000, 18_000)

@Composable
private fun PlaybackControls(
    state: NowPlayingUiState,
    actions: LyricsAppActions,
    motionLevel: MotionLevel,
    modifier: Modifier = Modifier,
) {
    val hasTrack = state.hasActiveSession
    val playbackPositionDescription = stringResource(R.string.ui_playback_position)
    val decreaseOffsetDescription = stringResource(R.string.ui_offset_decrease)
    val increaseOffsetDescription = stringResource(R.string.ui_offset_increase)
    val available = state.durationMs > 0L
    val progress = if (available) {
        (state.positionMs.toFloat() / state.durationMs).coerceIn(0f, 1f)
    } else {
        0f
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                horizontal = 0.dp,
                vertical = SignalDesignSystem.spacing.tiny,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        PremiumSeekBar(
            value = progress,
            durationMs = state.durationMs,
            onValueChange = actions.onSeek,
            enabled = available && state.canSeek,
            isPlaying = state.isPlaying,
            motionLevel = motionLevel,
            modifier = Modifier.semantics { contentDescription = playbackPositionDescription },
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(
                modifier = Modifier.widthIn(min = 64.dp).height(48.dp)
                    .semantics { contentDescription = decreaseOffsetDescription },
                contentPadding = PaddingValues(horizontal = 6.dp),
                enabled = hasTrack,
                onClick = { actions.onOffsetChange(state.offsetMs - 25L) },
            ) {
                Text(
                    text = "−25 ms",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                        alpha = if (hasTrack) 0.68f else 0.22f,
                    ),
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                SignalIconButton(
                    icon = Icons.Rounded.SkipPrevious,
                    contentDescription = stringResource(R.string.ui_previous_song),
                    onClick = actions.onSkipPrevious,
                    enabled = hasTrack && state.canSkipPrevious,
                    motionLevel = motionLevel,
                )
                SignalIconButton(
                    icon = if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = if (state.isPlaying) {
                        stringResource(R.string.ui_pause_playback)
                    } else {
                        stringResource(R.string.ui_resume_playback)
                    },
                    onClick = actions.onTogglePlayback,
                    prominent = true,
                    enabled = hasTrack && state.canTogglePlayback,
                    motionLevel = motionLevel,
                )
                SignalIconButton(
                    icon = Icons.Rounded.SkipNext,
                    contentDescription = stringResource(R.string.ui_next_song),
                    onClick = actions.onSkipNext,
                    enabled = hasTrack && state.canSkipNext,
                    motionLevel = motionLevel,
                )
            }
            TextButton(
                modifier = Modifier.widthIn(min = 64.dp).height(48.dp)
                    .semantics { contentDescription = increaseOffsetDescription },
                contentPadding = PaddingValues(horizontal = 6.dp),
                enabled = hasTrack,
                onClick = { actions.onOffsetChange(state.offsetMs + 25L) },
            ) {
                Text(
                    text = "+25 ms",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                        alpha = if (hasTrack) 0.68f else 0.22f,
                    ),
                )
            }
        }
        Text(
            text = stringResource(R.string.ui_lyrics_offset_format, state.offsetMs.toSignedMilliseconds()),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                alpha = if (state.offsetMs == 0L) 0.38f else 0.68f,
            ),
        )
    }
}

@Composable
private fun PremiumSeekBar(
    value: Float,
    durationMs: Long,
    onValueChange: (Float) -> Unit,
    enabled: Boolean,
    isPlaying: Boolean,
    motionLevel: MotionLevel,
    modifier: Modifier = Modifier,
) {
    val motion = rememberSignalMotion(motionLevel)
    val visualProgress = remember { Animatable(value.coerceIn(0f, 1f)) }
    var scrubValue by remember { mutableFloatStateOf(value) }
    var scrubbing by remember { mutableStateOf(false) }
    var pendingCommit by remember { mutableStateOf<Float?>(null) }
    var trackWidthPx by remember { mutableIntStateOf(1) }

    LaunchedEffect(value, durationMs, scrubbing, pendingCommit, enabled, isPlaying, motion.enabled) {
        val playbackValue = value.coerceIn(0f, 1f)
        val pending = pendingCommit
        if (pending != null) {
            if (shouldReleasePendingSeek(playbackValue, pending)) {
                pendingCommit = null
                visualProgress.snapTo(playbackValue)
            } else {
                return@LaunchedEffect
            }
        }
        if (scrubbing) return@LaunchedEffect
        scrubValue = playbackValue
        if (!motion.enabled || !isPlaying || durationMs <= 0L) {
            visualProgress.snapTo(playbackValue)
        } else {
            val drift = kotlin.math.abs(visualProgress.value - playbackValue)
            if (drift > 0.012f) {
                val correctionDuration = (110 + drift * 520f).toInt().coerceIn(110, 240)
                visualProgress.animateTo(
                    targetValue = playbackValue,
                    animationSpec = tween(
                        durationMillis = correctionDuration,
                        easing = LinearEasing,
                    ),
                )
            }
            if (visualProgress.value < 1f) {
                visualProgress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(
                        durationMillis = remainingSeekAnimationDurationMs(
                            progress = visualProgress.value,
                            durationMs = durationMs,
                        ),
                        easing = LinearEasing,
                    ),
                )
            }
        }
    }
    LaunchedEffect(pendingCommit) {
        val pending = pendingCommit ?: return@LaunchedEffect
        delay(1_200L.milliseconds)
        if (pendingCommit == pending) pendingCommit = null
    }

    val active = scrubbing
    val trackHeight by animateDpAsState(
        targetValue = if (active) 5.5.dp else 4.dp,
        animationSpec = SignalMotionSpecs.fast(motion),
        label = "seek track height",
    )
    val thumbSize by animateDpAsState(
        targetValue = if (active) 9.dp else 6.dp,
        animationSpec = SignalMotionSpecs.fast(motion),
        label = "seek thumb size",
    )
    val haptics = LocalHapticFeedback.current
    val displayProgress = seekDisplayProgress(
        playbackProgress = visualProgress.value,
        scrubProgress = scrubValue,
        isScrubbing = scrubbing,
        pendingProgress = pendingCommit,
    )
    val accent = seekBarAccentColor(
        accent = MaterialTheme.colorScheme.primary,
        background = MaterialTheme.colorScheme.background,
    )
    val neutralTrack = MaterialTheme.colorScheme.onSurface.copy(
        alpha = if (!enabled) 0.08f else if (active) 0.26f else 0.17f,
    )
    val trackInset = 6.dp

    Column(modifier = modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .onSizeChanged { trackWidthPx = it.width.coerceAtLeast(1) }
                .semantics {
                    progressBarRangeInfo = ProgressBarRangeInfo(displayProgress, 0f..1f)
                    if (enabled) {
                        setProgress { requested ->
                            val target = requested.coerceIn(0f, 1f)
                            scrubValue = target
                            pendingCommit = target
                            onValueChange(target)
                            true
                        }
                    }
                }
                .pointerInput(enabled, trackWidthPx) {
                    if (!enabled) return@pointerInput
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val insetPx = trackInset.toPx()
                        scrubbing = true
                        scrubValue = normalizedSeekProgress(down.position.x, trackWidthPx.toFloat(), insetPx)
                        down.consume()
                        var pressed = true
                        while (pressed) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id }
                            if (change == null || !change.pressed) {
                                pressed = false
                            } else {
                                scrubValue = normalizedSeekProgress(change.position.x, trackWidthPx.toFloat(), insetPx)
                                change.consume()
                            }
                        }
                        val committed = scrubValue.coerceIn(0f, 1f)
                        scrubbing = false
                        pendingCommit = committed
                        onValueChange(committed)
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                },
        ) {
            val maxThumbRadius = trackInset.toPx()
            val startX = maxThumbRadius
            val endX = (size.width - maxThumbRadius).coerceAtLeast(startX)
            val lineWidth = (endX - startX).coerceAtLeast(0f)
            val centerY = size.height / 2f
            val trackPx = trackHeight.toPx()
            val progressX = startX + lineWidth * displayProgress
            val radius = trackPx / 2f
            drawRoundRect(
                color = neutralTrack,
                topLeft = Offset(startX, centerY - radius),
                size = Size(lineWidth, trackPx),
                cornerRadius = CornerRadius(radius),
            )
            if (progressX > startX) {
                drawRoundRect(
                    color = accent.copy(alpha = if (enabled) 1f else 0.32f),
                    topLeft = Offset(startX, centerY - radius),
                    size = Size(progressX - startX, trackPx),
                    cornerRadius = CornerRadius(radius),
                )
            }
            // Glass lives only inside the thin track, never inside the 48 dp touch region.
            drawRoundRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color.White.copy(alpha = if (active) 0.18f else 0.12f),
                        Color.Transparent, Color.Black.copy(alpha = 0.10f)),
                    startY = centerY - radius, endY = centerY + radius,
                ),
                topLeft = Offset(startX, centerY - radius),
                size = Size(lineWidth, trackPx), cornerRadius = CornerRadius(radius),
            )
            drawRoundRect(
                color = Color.White.copy(alpha = if (active) 0.18f else 0.10f),
                topLeft = Offset(startX, centerY - radius),
                size = Size(lineWidth, trackPx), cornerRadius = CornerRadius(radius),
                style = Stroke(width = 0.6.dp.toPx()),
            )
            drawCircle(
                color = accent.copy(alpha = if (enabled) 1f else 0.32f),
                radius = thumbSize.toPx() / 2f,
                center = Offset(progressX, centerY),
            )
            drawCircle(
                color = Color.White.copy(alpha = if (enabled) 0.20f else 0.06f),
                radius = thumbSize.toPx() * 0.26f,
                center = Offset(progressX - thumbSize.toPx() * 0.08f,
                    centerY - thumbSize.toPx() * 0.08f),
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = trackInset),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                formatDuration((displayProgress * durationMs).toLong()),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.50f),
            )
            Text(
                formatDuration(durationMs),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.50f),
            )
        }
    }
}

internal fun seekDisplayProgress(
    playbackProgress: Float,
    scrubProgress: Float,
    isScrubbing: Boolean,
    pendingProgress: Float?,
): Float = when {
    isScrubbing -> scrubProgress
    pendingProgress != null -> pendingProgress
    else -> playbackProgress
}.coerceIn(0f, 1f)

internal fun shouldReleasePendingSeek(
    playbackProgress: Float,
    pendingProgress: Float,
    tolerance: Float = 0.012f,
): Boolean = kotlin.math.abs(playbackProgress - pendingProgress) <= tolerance

internal fun normalizedSeekProgress(
    pointerX: Float,
    widthPx: Float,
    horizontalInsetPx: Float,
): Float {
    val start = horizontalInsetPx.coerceAtLeast(0f)
    val usableWidth = (widthPx - start * 2f).coerceAtLeast(1f)
    return ((pointerX - start) / usableWidth).coerceIn(0f, 1f)
}

internal fun remainingSeekAnimationDurationMs(progress: Float, durationMs: Long): Int {
    val remaining = durationMs.coerceAtLeast(0L).toDouble() *
            (1f - progress.coerceIn(0f, 1f)).toDouble()
    return remaining.toLong().coerceIn(1L, Int.MAX_VALUE.toLong()).toInt()
}

internal fun seekBarAccentColor(accent: Color, background: Color): Color {
    val darkBackground = background.luminance() < 0.5f
    val target = if (darkBackground) Color.White else Color.Black
    var resolved = accent
    repeat(5) {
        if (seekBarContrast(resolved, background) >= 2.4f) return resolved
        resolved = lerp(resolved, target, 0.18f)
    }
    return resolved
}

private fun seekBarContrast(first: Color, second: Color): Float {
    val light = maxOf(first.luminance(), second.luminance()) + 0.05f
    val dark = minOf(first.luminance(), second.luminance()) + 0.05f
    return light / dark
}

@Composable
private fun LyricsPane(
    state: NowPlayingUiState,
    settings: SettingsUiState,
    actions: LyricsAppActions,
    modifier: Modifier = Modifier,
) {
    val activeLine = state.lyrics.firstOrNull { it.id == state.activeLineId }
    val motion = rememberSignalMotion(settings.motionLevel)
    Box(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp, vertical = 8.dp),
        ) {
            AnimatedContent(
                targetState = resolvedLyricsLoadState(state),
                transitionSpec = {
                    fadeIn(SignalMotionSpecs.content(motion)) togetherWith
                        fadeOut(SignalMotionSpecs.exit(motion))
                },
                modifier = Modifier.fillMaxSize(),
                label = "lyrics availability",
            ) { loadState ->
                when (loadState) {
                    LyricsLoadState.IDLE -> LyricsMessageState(
                        icon = Icons.Rounded.MusicOff,
                        title = stringResource(R.string.ui_lyrics_idle_title),
                        detail = stringResource(R.string.ui_lyrics_idle_detail),
                        modifier = Modifier.fillMaxSize(),
                    )
                    LyricsLoadState.SEARCHING -> LyricsLoadingSkeleton(
                        motionLevel = settings.motionLevel,
                        modifier = Modifier.fillMaxSize(),
                    )
                    LyricsLoadState.NOT_FOUND -> LyricsMessageState(
                        icon = Icons.Rounded.MusicNote,
                        title = stringResource(R.string.ui_lyrics_not_found_title),
                        detail = stringResource(R.string.ui_lyrics_not_found_detail),
                        modifier = Modifier.fillMaxSize(),
                    )
                    LyricsLoadState.ERROR -> LyricsMessageState(
                        icon = Icons.Rounded.Refresh,
                        title = stringResource(R.string.ui_lyrics_error_title),
                        detail = state.lyricsLoadMessage ?: stringResource(R.string.ui_lyrics_error_detail),
                        actionLabel = stringResource(R.string.ui_retry),
                        onAction = actions.onRetryLyrics,
                        modifier = Modifier.fillMaxSize(),
                    )
                    LyricsLoadState.AVAILABLE -> when (settings.lyricsDisplayMode) {
                        LyricsDisplayMode.FULL_LYRICS -> AutoScrollingLyrics(
                            lines = state.lyrics,
                            activeLineId = state.activeLineId,
                            motionLevel = settings.motionLevel,
                            showTranslation = true,
                            modifier = Modifier.fillMaxSize(),
                            canSeek = state.canSeek,
                            alignmentOverride = settings.lyricsAlignment,
                            timeline = state.timeline,
                            visualStyle = settings.karaokeStyle,
                            artworkUri = state.artworkUri,
                            onSeekLine = actions.onSeekLine,
                            isPlaying = state.isPlaying,
                            lyricsTextScale = settings.lyricsTextScale,
                        )

                        LyricsDisplayMode.CURRENT_WORD -> CurrentWordLyrics(
                            activeLine = activeLine,
                            activeWordId = state.activeWordId,
                            timeline = state.timeline,
                            fallbackToLine = settings.currentWordFallbackToLine,
                            motionLevel = settings.motionLevel,
                            visualStyle = settings.karaokeStyle,
                            artworkUri = state.artworkUri,
                            modifier = Modifier.fillMaxSize(),
                            textStyle = MaterialTheme.typography.displaySmall.copy(
                                fontSize = scaledLyricsSp(
                                    MaterialTheme.typography.displaySmall.fontSize.value,
                                    settings.lyricsTextScale,
                                ).sp,
                                lineHeight = scaledLyricsSp(
                                    MaterialTheme.typography.displaySmall.lineHeight.value,
                                    settings.lyricsTextScale,
                                ).sp,
                                letterSpacing = scaledLyricsSp(
                                    MaterialTheme.typography.displaySmall.letterSpacing.value,
                                    settings.lyricsTextScale,
                                ).sp,
                            ),
                        )
                    }
                }
            }
        }
    }
}

internal fun resolvedLyricsLoadState(state: NowPlayingUiState): LyricsLoadState = when {
    state.lyrics.isNotEmpty() -> LyricsLoadState.AVAILABLE
    !state.hasActiveSession -> LyricsLoadState.IDLE
    state.lyricsLoadState == LyricsLoadState.NOT_FOUND -> LyricsLoadState.NOT_FOUND
    state.lyricsLoadState == LyricsLoadState.ERROR -> LyricsLoadState.ERROR
    else -> LyricsLoadState.SEARCHING
}

@Composable
private fun LyricsMessageState(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    detail: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
) {
    Column(
        modifier = modifier.padding(horizontal = 2.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.Start,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
            modifier = Modifier.size(26.dp),
        )
        Spacer(Modifier.height(14.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = detail,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.76f),
            modifier = Modifier.widthIn(max = 420.dp),
        )
        actionLabel?.let { label ->
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onAction) { Text(label) }
        }
    }
}

@Composable
private fun LyricsLoadingSkeleton(
    motionLevel: MotionLevel,
    modifier: Modifier = Modifier,
) {
    val motion = rememberSignalMotion(motionLevel)
    val shimmerPhase = if (motion.enabled) {
        val shimmer = rememberInfiniteTransition(label = "lyrics search shimmer")
        shimmer.animateFloat(
            initialValue = -0.5f,
            targetValue = 1.5f,
            animationSpec = infiniteRepeatable(
                animation = tween(1_450, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "lyrics search shimmer phase",
        ).value
    } else {
        -0.5f
    }
    Column(
        modifier = modifier.padding(top = 72.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Text(
            text = stringResource(R.string.ui_searching_lyrics),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.54f),
        )
        listOf(0.72f, 0.9f, 0.58f).forEachIndexed { index, widthFraction ->
            val base = MaterialTheme.colorScheme.onSurface.copy(
                alpha = if (index == 1) 0.13f else 0.075f,
            )
            val sheen = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.075f)
            Box(
                modifier = Modifier
                    .fillMaxWidth(widthFraction)
                    .height(if (index == 1) 30.dp else 22.dp)
                    .drawWithCache {
                        val radius = 8.dp.toPx()
                        val highlightCenter = shimmerPhase * size.width
                        val shimmerBrush = Brush.linearGradient(
                            colors = listOf(Color.Transparent, sheen, Color.Transparent),
                            start = Offset(highlightCenter - size.width * 0.32f, 0f),
                            end = Offset(highlightCenter + size.width * 0.32f, size.height),
                        )
                        onDrawBehind {
                            drawRoundRect(base, cornerRadius = CornerRadius(radius, radius))
                            if (motion.enabled) {
                                drawRoundRect(shimmerBrush, cornerRadius = CornerRadius(radius, radius))
                            }
                        }
                    },
            ) { }
        }
    }
}

@Composable
private fun Artwork(
    artworkUri: String?,
    title: String?,
    hasTrack: Boolean,
    motionLevel: MotionLevel,
    modifier: Modifier = Modifier,
) {
    SongArtwork(
        artworkUri = artworkUri,
        title = title,
        modifier = modifier,
        cornerRadius = 22,
        emptyLabel = if (hasTrack) null else stringResource(R.string.ui_no_session),
        motionLevel = motionLevel,
        showBorder = false,
    )
}

private data class TrackVisual(
    val title: String?,
    val artist: String?,
    val album: String?,
    val provider: String?,
    val artworkUri: String?,
    val hasTrack: Boolean,
) {
    companion object {
        fun from(state: NowPlayingUiState): TrackVisual = TrackVisual(
            title = state.title,
            artist = state.artist,
            album = state.album,
            provider = state.connectionLabel.takeIf { state.hasActiveSession && it.isNotBlank() },
            artworkUri = state.artworkUri,
            hasTrack = state.hasActiveSession,
        )
    }
}

private fun trackChangeTransform(motion: SignalMotionScheme): ContentTransform = when {
    !motion.enabled -> EnterTransition.None togetherWith ExitTransition.None
    motion.reduced -> fadeIn(SignalMotionSpecs.immersive(motion)) togetherWith
        fadeOut(SignalMotionSpecs.exit(motion))
    else -> (
        fadeIn(SignalMotionSpecs.immersive(motion)) +
            scaleIn(initialScale = 0.985f, animationSpec = SignalMotionSpecs.immersive(motion)) +
            slideInVertically(SignalMotionSpecs.immersive(motion)) { it / 18 }
        ) togetherWith (
        fadeOut(SignalMotionSpecs.exit(motion)) +
            scaleOut(targetScale = 1.006f, animationSpec = SignalMotionSpecs.exit(motion)) +
            slideOutVertically(SignalMotionSpecs.exit(motion)) { -it / 24 }
        )
}

private fun Long.toSignedMilliseconds(): String =
    (if (this > 0L) "+" else "") + this + " ms"


private fun formatDuration(milliseconds: Long): String {
    val totalSeconds = milliseconds.coerceAtLeast(0L) / 1_000L
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return minutes.toString() + ":" + seconds.toString().padStart(2, '0')
}
