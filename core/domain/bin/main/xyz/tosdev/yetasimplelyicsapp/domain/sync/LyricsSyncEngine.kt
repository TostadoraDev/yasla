package xyz.tosdev.yetasimplelyicsapp.domain.sync

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import xyz.tosdev.yetasimplelyicsapp.domain.lyrics.LyricsDocument
import xyz.tosdev.yetasimplelyicsapp.domain.lyrics.LyricsLine
import xyz.tosdev.yetasimplelyicsapp.domain.lyrics.LyricsWord
import xyz.tosdev.yetasimplelyicsapp.domain.playback.MonotonicClock
import xyz.tosdev.yetasimplelyicsapp.domain.playback.PlaybackSnapshot
import xyz.tosdev.yetasimplelyicsapp.domain.playback.SystemMonotonicClock

data class SyncState(
    val document: LyricsDocument? = null,
    val playback: PlaybackSnapshot? = null,
    val lyricsPositionMs: Long? = null,
    val activeLine: LyricsLine? = null,
    val currentLineIndex: Int = -1,
    val previousLine: LyricsLine? = null,
    val nextLine: LyricsLine? = null,
    val activeWord: LyricsWord? = null,
    val lineProgress: Float = 0f,
    val wordProgress: Float = 0f,
    val offsetMs: Long = 0L,
)

class LyricsSyncEngine(
    private val clock: MonotonicClock = SystemMonotonicClock,
) {
    private val lock = Any()
    private var document: LyricsDocument? = null
    private var playback: PlaybackSnapshot? = null
    private var offsetMs: Long = 0L
    private var indexedTimedLines: List<LyricsLine> = emptyList()
    private var indexedTimedWordsByLineId: Map<String, List<LyricsWord>> = emptyMap()

    private val mutableState = MutableStateFlow(SyncState())
    val state: StateFlow<SyncState> = mutableState.asStateFlow()

    fun updateLyrics(newDocument: LyricsDocument?) = synchronized(lock) {
        replaceDocument(
            newDocument?.takeIf { candidate ->
                playback?.trackKey == null || playback?.trackKey == candidate.trackKey
            },
        )
        recompute(clock.nowElapsedMs())
    }

    fun updatePlayback(newPlayback: PlaybackSnapshot?) = synchronized(lock) {
        val previousTrack = playback?.trackKey
        playback = newPlayback
        if (previousTrack != null && newPlayback?.trackKey != previousTrack) {
            replaceDocument(null)
        } else if (
            document != null &&
            newPlayback?.trackKey != null &&
            document?.trackKey != newPlayback.trackKey
        ) {
            replaceDocument(null)
        }
        recompute(clock.nowElapsedMs())
    }

    fun updateOffset(newOffsetMs: Long) = synchronized(lock) {
        offsetMs = newOffsetMs
        recompute(clock.nowElapsedMs())
    }

    fun sample(elapsedRealtimeMs: Long = clock.nowElapsedMs()): SyncState = synchronized(lock) {
        recompute(elapsedRealtimeMs)
    }

    private fun replaceDocument(newDocument: LyricsDocument?) {
        document = newDocument
        indexedTimedLines = newDocument
            ?.lines
            ?.filter { it.startTimeMs != null }
            ?.sortedWith(compareBy<LyricsLine>({ it.startTimeMs }, { it.order }))
            .orEmpty()
        indexedTimedWordsByLineId = indexedTimedLines.associate { line ->
            line.id to line.words
                .filter { it.startTimeMs != null }
                .sortedBy { it.startTimeMs }
        }
    }

    private fun recompute(elapsedRealtimeMs: Long): SyncState {
        val currentDocument = document
        val currentPlayback = playback
        val playerPosition = currentPlayback?.estimatedPositionAt(elapsedRealtimeMs)
        val lyricsPosition = playerPosition?.minus(offsetMs)?.coerceAtLeast(0L)
        val timedLines = indexedTimedLines

        val activeIndex = if (lyricsPosition == null) {
            -1
        } else {
            findActiveLineIndex(
                lines = timedLines,
                positionMs = lyricsPosition,
                trackDurationMs = currentPlayback.durationMs,
            )
        }

        val active = timedLines.getOrNull(activeIndex)
        val previous = timedLines.getOrNull(activeIndex - 1)
        val next = timedLines.getOrNull(activeIndex + 1)
        val vocalEnd = active?.endTimeMs ?: next?.startTimeMs ?: currentPlayback?.durationMs

        val activeWordState = if (active != null && lyricsPosition != null) {
            resolveWord(active, vocalEnd, lyricsPosition)
        } else {
            null
        }

        return SyncState(
            document = currentDocument,
            playback = currentPlayback,
            lyricsPositionMs = lyricsPosition,
            activeLine = active,
            currentLineIndex = activeIndex,
            previousLine = previous,
            nextLine = next,
            activeWord = activeWordState?.word,
            lineProgress = if (vocalEnd != null && lyricsPosition != null && lyricsPosition >= vocalEnd) {
                1f
            } else {
                progress(
                    lyricsPosition,
                    active?.startTimeMs,
                    vocalEnd,
                )
            },
            wordProgress = activeWordState?.progress ?: 0f,
            offsetMs = offsetMs,
        ).also { mutableState.value = it }
    }

    private fun findActiveLineIndex(
        lines: List<LyricsLine>,
        positionMs: Long,
        trackDurationMs: Long?,
    ): Int {
        for (index in lines.lastIndex downTo 0) {
            val line = lines[index]
            val start = line.startTimeMs ?: continue
            val nextStart = lines.getOrNull(index + 1)?.startTimeMs
            val end = nextStart
                ?: line.endTimeMs
                ?: trackDurationMs
                ?: Long.MAX_VALUE
            if (intervalContains(positionMs, start, end)) return index
        }
        return -1
    }

    private fun resolveWord(
        line: LyricsLine,
        lineEndMs: Long?,
        positionMs: Long,
    ): ActiveWord? {
        val timedWords = indexedTimedWordsByLineId[line.id].orEmpty()

        for (index in timedWords.lastIndex downTo 0) {
            val word = timedWords[index]
            val start = word.startTimeMs ?: continue
            val end = word.endTimeMs
                ?: timedWords.getOrNull(index + 1)?.startTimeMs
                ?: lineEndMs
                ?: Long.MAX_VALUE
            if (intervalContains(positionMs, start, end)) {
                return ActiveWord(
                    word = word,
                    progress = progress(positionMs, start, end),
                )
            }
        }
        return null
    }

    private fun intervalContains(positionMs: Long, startMs: Long, endMs: Long): Boolean =
        if (endMs <= startMs) positionMs == startMs else positionMs in startMs until endMs

    private fun progress(positionMs: Long?, startMs: Long?, endMs: Long?): Float {
        if (positionMs == null || startMs == null || endMs == null) return 0f
        if (endMs <= startMs) return if (positionMs >= startMs) 1f else 0f
        return ((positionMs - startMs).toDouble() / (endMs - startMs))
            .coerceIn(0.0, 1.0)
            .toFloat()
    }

    private data class ActiveWord(
        val word: LyricsWord,
        val progress: Float,
    )
}
