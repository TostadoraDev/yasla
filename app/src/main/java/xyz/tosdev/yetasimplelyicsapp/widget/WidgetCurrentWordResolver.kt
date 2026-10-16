package xyz.tosdev.yetasimplelyicsapp.widget

import xyz.tosdev.yetasimplelyicsapp.domain.lyrics.LyricsWord

/**
 * Keeps widget presentation on semantic [LyricsWord] boundaries. Child TTML/AMLL segments never
 * enter this resolver. During a timing gap it returns the most recent word whose start was reached,
 * which also makes seeks deterministic without carrying mutable state from a future position.
 */
internal fun resolveStableWidgetWord(
    currentWord: LyricsWord?,
    lyricsPositionMs: Long?,
    timedWords: List<LyricsWord>,
): LyricsWord? {
    currentWord?.takeIf { it.text.isNotBlank() }?.let { return it }
    val positionMs = lyricsPositionMs ?: return null

    var low = 0
    var high = timedWords.lastIndex
    var latest: LyricsWord? = null
    while (low <= high) {
        val middle = (low + high).ushr(1)
        val candidate = timedWords[middle]
        val startMs = candidate.startTimeMs
        if (startMs == null || startMs > positionMs) {
            high = middle - 1
        } else {
            if (candidate.text.isNotBlank()) latest = candidate
            low = middle + 1
        }
    }
    return latest
}
