package xyz.tosdev.yetasimplelyicsapp.ui.components

import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.AndroidUiDispatcher
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import xyz.tosdev.yetasimplelyicsapp.R
import xyz.tosdev.yetasimplelyicsapp.domain.lyrics.LyricsAlignment
import xyz.tosdev.yetasimplelyicsapp.ui.KaraokeVisualStyle
import xyz.tosdev.yetasimplelyicsapp.ui.LyricLineUi
import xyz.tosdev.yetasimplelyicsapp.ui.LyricsTimeline
import xyz.tosdev.yetasimplelyicsapp.ui.LyricsTimingMode
import xyz.tosdev.yetasimplelyicsapp.ui.MotionLevel
import xyz.tosdev.yetasimplelyicsapp.ui.normalizeLyricsTextScale
import xyz.tosdev.yetasimplelyicsapp.ui.scaledLyricsSp
import xyz.tosdev.yetasimplelyicsapp.ui.designsystem.rememberSignalMotion
import kotlin.math.abs
import kotlin.math.sqrt

internal fun amllFrameDeltaSeconds(previousFrameNanos: Long, frameNanos: Long): Float =
    if (previousFrameNanos == 0L) 0f else
        ((frameNanos - previousFrameNanos).coerceAtLeast(0L) / 1_000_000_000.0).toFloat()

@Composable
internal fun AmllLyricsViewport(
    lines: List<LyricLineUi>, activeLineId: String?, motionLevel: MotionLevel,
    modifier: Modifier, showTranslation: Boolean, canSeek: Boolean,
    alignmentOverride: String, timeline: LyricsTimeline?, visualStyle: KaraokeVisualStyle,
    artworkUri: String?, onSeekLine: (String) -> Unit, isPlaying: Boolean,
    lyricsTextScale: Float = 1f,
) {
    val list = rememberLazyListState()
    val motion = rememberSignalMotion(motionLevel)
    val effectiveLevel = if (motion.enabled) motionLevel else MotionLevel.OFF
    val density = LocalDensity.current
    val normalizedTextScale = normalizeLyricsTextScale(lyricsTextScale)
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val sourceKey = amllLyricsSourceKey(lines)
    val rows = remember(sourceKey) { mutableMapOf<String, AmllRowMotion>() }
    val windows = remember(sourceKey) { amllVisualWindows(lines) }
    val visualTimeline = remember(sourceKey) { AmllVisualTimeline(windows) }
    val latestLines by rememberUpdatedState(lines)
    val latestIndex by rememberUpdatedState(lines.indexOfFirst { it.id == activeLineId })
    val latestPlaying by rememberUpdatedState(isPlaying)
    val latestTimeline by rememberUpdatedState(timeline)
    var following by remember { mutableStateOf(true) }
    val colors = rememberKaraokeColors(visualStyle, artworkUri, motionLevel)
    val userScroll = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(
                available: androidx.compose.ui.geometry.Offset,
                source: NestedScrollSource
            ): androidx.compose.ui.geometry.Offset {
                if (source == NestedScrollSource.UserInput && available.y != 0f) following = false
                return androidx.compose.ui.geometry.Offset.Zero
            }
        }
    }

    // A single coordinator owns all render-frame motion. Media time comes from the app timeline.
    LaunchedEffect(sourceKey, effectiveLevel, lifecycle) {
        var initialized = false
        var lastIndex = latestIndex.coerceAtLeast(0)
        var previousFrame = 0L
        var previousPosition: Long? = null
        var scrollDistance = 0f
        var followedLastFrame = true
        var stiffness = 90f
        var damping = 15f
        val scroll = AmllSpring(0f, 0.9f, stiffness, damping)
        // Keep the trajectory across visibility changes, but stop Vsync work in the background.
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            // Use Choreographer's frame clock independently of recomposition/draw invalidations.
            withContext(AndroidUiDispatcher.Main) {
                previousFrame = 0L
                while (isActive) {
                    val frameTime = withFrameNanos { it }
                    val dt = amllFrameDeltaSeconds(previousFrame, frameTime)
                    previousFrame = frameTime
                    val current = latestLines
                    val height = list.layoutInfo.viewportSize.height
                    if (height <= 0 || current.isEmpty()) continue
                    val now = latestTimeline?.positionMs
                    val seeking = lyricsPlaybackDiscontinuity(previousPosition, now, dt)
                    if (seeking) rows.values.forEach { it.resetWordClock() }
                    previousPosition = now
                    val visualState = visualTimeline.update(
                        positionMs = now,
                        seeking = seeking,
                        fallbackHotIndices = current.indices.filterTo(linkedSetOf()) { current[it].isActive },
                    )
                    val buffered = visualState.bufferedIndices
                    val index = visualState.scrollIndex.coerceIn(current.indices)
                    if (!initialized) {
                        list.scrollToItem(
                            index,
                            -list.layoutInfo.viewportStartOffset - lyricFocusOffsetPx(height)
                        )
                        initialized = true
                    }
                    if (index != lastIndex || seeking) {
                        stiffness = if (seeking) 90f else {
                            val interval = amllVisualIntervalMs(current, windows, index)
                            interval?.let(::amllVerticalStiffness) ?: stiffness
                        }
                        damping = if (seeking) 15f else 2.2f * sqrt(stiffness)
                        scroll.setParameters(0.9f, stiffness, damping)
                    }
                    lastIndex = index
                    val geometry = list.layoutInfo.visibleItemsInfo.map {
                        LyricsItemGeometry(it.index, it.offset, it.size)
                    }
                    val error = lyricTargetErrorPx(
                        index, geometry,
                        list.layoutInfo.viewportStartOffset, height
                    ) ?: 0f
                    if (following && !followedLastFrame) {
                        scrollDistance = 0f
                        scroll.snap(0f)
                    }
                    if (following) {
                        scroll.retarget(scrollDistance + error)
                        val next =
                            if (effectiveLevel == MotionLevel.OFF || effectiveLevel == MotionLevel.REDUCED) {
                                scroll.snap(scrollDistance + error)
                                scroll.position
                            } else scroll.step(dt)
                        val consumed = list.scrollBy(next - scrollDistance)
                        scrollDistance += consumed
                        if (abs(consumed) < 0.001f && abs(next - scrollDistance) > 0.5f) scroll.snap(
                            scrollDistance
                        )
                    }
                    followedLastFrame = following
                    val layout = list.layoutInfo
                    val updatedGeometry = layout.visibleItemsInfo.map {
                        LyricsItemGeometry(
                            it.index,
                            it.offset,
                            it.size
                        )
                    }
                    val updatedError = lyricTargetErrorPx(
                        index,
                        updatedGeometry,
                        layout.viewportStartOffset,
                        height
                    ) ?: 0f
                    val lastActive = buffered.maxOrNull() ?: index
                    var stagger = 0f
                    var staggerStep = 0.05f
                    layout.visibleItemsInfo.forEach { item ->
                        val line = current.getOrNull(item.index) ?: return@forEach
                        val actualTop =
                            lyricViewportTop(item.offset, layout.viewportStartOffset).toFloat()
                        val row = rows.getOrPut(line.id) { AmllRowMotion(height * 2f) }
                        val dynamic = line.timingMode == LyricsTimingMode.WORD_SYNCED
                        val active = item.index in buffered
                        val desiredTop = if (following) actualTop - updatedError else actualTop
                        if (!following || effectiveLevel == MotionLevel.OFF || effectiveLevel == MotionLevel.REDUCED) row.position.snap(
                            desiredTop
                        )
                        row.retarget(
                            top = desiredTop,
                            isActive = active,
                            isPlaying = latestPlaying,
                            dynamic = dynamic,
                            blurDp = if (following) amllLineBlur(
                                item.index, index, lastActive, active,
                                layout.viewportSize.width / density.density <= 1024f
                            ) else 0f,
                            stiffness = stiffness,
                            damping = damping,
                            delay = if (seeking || !following) 0f else stagger,
                            level = effectiveLevel,
                            motionTailMs = if (!dynamic || effectiveLevel == MotionLevel.OFF || effectiveLevel == MotionLevel.REDUCED) 0f else
                                (line.words.maxOfOrNull {
                                    ((it.endTimeMs ?: 0) - (it.startTimeMs ?: 0)).coerceAtLeast(
                                        1_000L
                                    )
                                }
                                    ?: 1_000L) * 2.2f + 400f,
                        )
                        row.step(dt, now, dynamic, effectiveLevel)
                        if (desiredTop + item.size >= 0f && !seeking) {
                            stagger += staggerStep
                            if (item.index >= index) staggerStep /= 1.05f
                        }
                    }
                }
            }
        }
    }

    BoxWithConstraints(modifier.clipToBounds()) {
        val top = maxHeight * LYRICS_FOCUS_FRACTION
        LazyColumn(
            state = list,
            modifier = Modifier
                .fillMaxWidth()
                .then(Modifier.nestedScroll(userScroll)),
            contentPadding = PaddingValues(top = top, bottom = maxHeight - top),
        ) {
            itemsIndexed(lines, key = { _, line -> line.id }) { index, line ->
                val row = remember(sourceKey, line.id) {
                    rows.getOrPut(line.id) {
                        AmllRowMotion(list.layoutInfo.viewportSize.height * 2f)
                    }
                }
                val alignment = lyricsRowAlignment(lines, index, alignmentOverride)
                var previousBlur = -1f
                var cachedBlur: androidx.compose.ui.graphics.RenderEffect? = null
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer {
                            val frame = row.frame
                            val layout = list.layoutInfo
                            val item = layout.visibleItemsInfo.firstOrNull { it.key == line.id }
                            translationY = if (item == null) 0f else
                                frame.topPx - lyricViewportTop(
                                    item.offset,
                                    layout.viewportStartOffset
                                )
                            scaleX = frame.scale
                            scaleY = frame.scale
                            alpha = frame.opacity
                            transformOrigin = TransformOrigin(
                                when (alignment) {
                                    LyricsAlignment.RIGHT -> 1f
                                    LyricsAlignment.CENTER -> 0.5f
                                    else -> 0f
                                }, 0.5f
                            )
                            if (Build.VERSION.SDK_INT >= 31) {
                                val radius = (frame.blurDp * density.density * 2f).toInt() / 2f
                                if (radius != previousBlur) {
                                    cachedBlur = if (radius <= 0.05f) null else
                                        android.graphics.RenderEffect.createBlurEffect(
                                            radius, radius,
                                            android.graphics.Shader.TileMode.DECAL
                                        ).asComposeRenderEffect()
                                    previousBlur = radius
                                }
                                renderEffect = cachedBlur
                            }
                        }
                        .padding(start = 2.dp, end = 2.dp, top = 8.dp, bottom = 12.dp)
                        .clickable(enabled = canSeek) { onSeekLine(line.id) },
                    horizontalAlignment = when (alignment) {
                        LyricsAlignment.RIGHT -> Alignment.End
                        LyricsAlignment.CENTER -> Alignment.CenterHorizontally
                        else -> Alignment.Start
                    },
                ) {
                    AmllKaraokeText(
                        line, row, { timeline?.positionMs },
                        textStyle = if (line.isBackground) MaterialTheme.typography.headlineSmall.copy(
                            fontSize = scaledLyricsSp(21f, normalizedTextScale).sp,
                            lineHeight = scaledLyricsSp(27f, normalizedTextScale).sp,
                            fontFamily = MaterialTheme.typography.headlineLarge.fontFamily,
                            fontStyle = FontStyle.Normal,
                            letterSpacing = scaledLyricsSp(-0.2f, normalizedTextScale).sp,
                        ) else MaterialTheme.typography.headlineLarge.copy(
                            fontSize = scaledLyricsSp(36f, normalizedTextScale).sp,
                            lineHeight = scaledLyricsSp(43f, normalizedTextScale).sp,
                            letterSpacing = scaledLyricsSp(-0.75f, normalizedTextScale).sp,
                        ),
                        colors = colors, alignment = alignment, motionLevel = effectiveLevel,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (showTranslation && !line.translation.isNullOrBlank()) Text(
                        line.translation,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = scaledLyricsSp(
                                MaterialTheme.typography.bodyMedium.fontSize.value,
                                normalizedTextScale,
                            ).sp,
                            lineHeight = scaledLyricsSp(
                                MaterialTheme.typography.bodyMedium.lineHeight.value,
                                normalizedTextScale,
                            ).sp,
                            letterSpacing = scaledLyricsSp(
                                MaterialTheme.typography.bodyMedium.letterSpacing.value,
                                normalizedTextScale,
                            ).sp,
                        ),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                        modifier = Modifier.padding(top = 4.dp),
                        textAlign = when (alignment) {
                            LyricsAlignment.RIGHT -> TextAlign.Right
                            LyricsAlignment.CENTER -> TextAlign.Center
                            else -> TextAlign.Left
                        },
                    )
                }
            }
        }
        if (!following && latestIndex >= 0) TextButton(onClick = { following = true }) {
            Text(stringResource(R.string.ui_return_to_current_lyric))
        }
    }
}
