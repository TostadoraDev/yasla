package xyz.tosdev.yetasimplelyicsapp

import xyz.tosdev.yetasimplelyicsapp.domain.lyrics.LyricsWord
import xyz.tosdev.yetasimplelyicsapp.domain.playback.PlaybackSnapshot
import xyz.tosdev.yetasimplelyicsapp.domain.playback.PlaybackStatus
import kotlin.math.ceil

/** Absolute deadline from the media report, not from the previous word's completion. */
internal fun wordDeadlineElapsedMs(playback: PlaybackSnapshot?, sourceStartMs: Long?, offsetMs: Long): Long {
    val report = playback?.reportedPositionMs ?: return -1
    if (sourceStartMs == null || playback.status != PlaybackStatus.PLAYING ||
        !playback.speed.isFinite() || playback.speed <= 0f) return -1
    return (playback.reportedAtElapsedMs.toDouble() + ceil(
        (sourceStartMs.toDouble() + offsetMs - report) / playback.speed,
    )).coerceIn(0.0, Long.MAX_VALUE.toDouble()).toLong()
}

/** The sorted semantic index is built once when the document changes; no segment traversal. */
internal fun nextSemanticWidgetWord(words: List<LyricsWord>, positionMs: Long?): LyricsWord? {
    if (positionMs == null) return words.firstOrNull()
    var low = 0
    var high = words.size
    while (low < high) {
        val middle = (low + high).ushr(1)
        if ((words[middle].startTimeMs ?: Long.MAX_VALUE) <= positionMs) low = middle + 1
        else high = middle
    }
    return words.getOrNull(low)
}
