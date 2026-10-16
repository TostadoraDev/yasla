package xyz.tosdev.yetasimplelyicsapp

import kotlin.math.ceil

/**
 * Returns a one-shot delay to the next semantic lyric boundary. This is deliberately not a
 * polling interval: callers schedule one timer, publish that boundary, then calculate the next.
 */
internal fun nextCueBoundaryDelayMs(
    boundaries: LongArray,
    lyricsPositionMs: Long,
    playbackSpeed: Float,
): Long? {
    if (boundaries.isEmpty() || playbackSpeed <= 0f) return null
    val found = boundaries.binarySearch(lyricsPositionMs)
    val insertionPoint = if (found >= 0) {
        var index = found + 1
        while (index < boundaries.size && boundaries[index] <= lyricsPositionMs) index++
        index
    } else {
        -found - 1
    }
    val nextBoundary = boundaries.getOrNull(insertionPoint) ?: return null
    val lyricDeltaMs = (nextBoundary - lyricsPositionMs).coerceAtLeast(0L)
    return ceil(lyricDeltaMs.toDouble() / playbackSpeed.toDouble())
        .toLong()
        .coerceAtLeast(1L)
}
