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

data class LineSyncState(
    val line: LyricsLine,
    val activeWord: LyricsWord? = null,
    val lineProgress: Float = 0f,
    val wordProgress: Float = 0f,
)

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
    val activeLines: List<LineSyncState> = emptyList(),
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
    private var indexedLastWordEndByLineId: Map<String, Long> = emptyMap()
    private var indexedNextStartByLineId: Map<String, Long> = emptyMap()
    private var groupIdsWithBackground: Set<String> = emptySet()

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
        indexedLastWordEndByLineId = indexedTimedWordsByLineId.mapNotNull { (lineId, words) ->
            words.mapNotNull(LyricsWord::endTimeMs).maxOrNull()?.let { lineId to it }
        }.toMap()
        groupIdsWithBackground = indexedTimedLines
            .asSequence()
            .filter(LyricsLine::isBackground)
            .mapNotNull(LyricsLine::groupId)
            .toSet()
        indexedNextStartByLineId = indexedTimedLines.mapNotNull { line ->
            val start = line.startTimeMs ?: return@mapNotNull null
            indexedTimedLines.firstOrNull { candidate ->
                candidate.isBackground == line.isBackground &&
                    (candidate.startTimeMs ?: Long.MIN_VALUE) > start
            }?.startTimeMs?.let { line.id to it }
        }.toMap()
    }

    private fun recompute(elapsedRealtimeMs: Long): SyncState {
        val currentDocument = document
        val currentPlayback = playback
        val playerPosition = currentPlayback?.estimatedPositionAt(elapsedRealtimeMs)
        val lyricsPosition = playerPosition?.minus(offsetMs)?.coerceAtLeast(0L)
        val timedLines = indexedTimedLines

        val active = if (lyricsPosition == null) {
            null
        } else {
            chooseAnchorLine(timedLines, lyricsPosition, currentPlayback?.durationMs)
        }
        val activeIndex = active?.let(timedLines::indexOf) ?: -1
        val previous = timedLines.getOrNull(activeIndex - 1)
        val next = timedLines.getOrNull(activeIndex + 1)

        val singingLines = if (lyricsPosition == null) {
            emptyList()
        } else {
            timedLines.filter { line ->
                val start = line.startTimeMs ?: return@filter false
                intervalContains(
                    positionMs = lyricsPosition,
                    startMs = start,
                    endMs = effectiveLineEnd(line, currentPlayback?.durationMs),
                )
            }
        }
        val visibleActiveLines = buildList {
            if (active != null) add(active)
            singingLines.forEach { if (it.id != active?.id) add(it) }
        }.map { line ->
            resolveLineState(
                line = line,
                positionMs = lyricsPosition,
                lineEndMs = effectiveLineEnd(line, currentPlayback?.durationMs),
            )
        }
        val anchorState = visibleActiveLines.firstOrNull { it.line.id == active?.id }

        return SyncState(
            document = currentDocument,
            playback = currentPlayback,
            lyricsPositionMs = lyricsPosition,
            activeLine = active,
            currentLineIndex = activeIndex,
            previousLine = previous,
            nextLine = next,
            activeWord = anchorState?.activeWord,
            lineProgress = anchorState?.lineProgress ?: 0f,
            wordProgress = anchorState?.wordProgress ?: 0f,
            activeLines = visibleActiveLines,
            offsetMs = offsetMs,
        ).also { mutableState.value = it }
    }

    private fun chooseAnchorLine(
        lines: List<LyricsLine>,
        positionMs: Long,
        trackDurationMs: Long?,
    ): LyricsLine? {
        if (trackDurationMs != null && positionMs >= trackDurationMs) return null

        val currentlySinging = lines.filter { line ->
            val start = line.startTimeMs ?: return@filter false
            intervalContains(positionMs, start, effectiveLineEnd(line, trackDurationMs))
        }
        currentlySinging
            .filterNot(LyricsLine::isBackground)
            .maxWithOrNull(compareBy<LyricsLine>({ it.startTimeMs }, { it.order }))
            ?.let { return it }
        currentlySinging
            .filter(LyricsLine::isBackground)
            .maxWithOrNull(compareBy<LyricsLine>({ it.startTimeMs }, { it.order }))
            ?.let { return it }

        // Preserve the established karaoke behavior through instrumental gaps. Background
        // vocals never take ownership after their actual interval has ended.
        return lines
            .asSequence()
            .filterNot(LyricsLine::isBackground)
            .filter { (it.startTimeMs ?: Long.MAX_VALUE) <= positionMs }
            .maxWithOrNull(compareBy<LyricsLine>({ it.startTimeMs }, { it.order }))
    }

    private fun effectiveLineEnd(
        line: LyricsLine,
        trackDurationMs: Long?,
    ): Long {
        val lastWordEnd = indexedLastWordEndByLineId[line.id]
        val hasGroupedBackground = !line.isBackground &&
            line.groupId != null &&
            line.groupId in groupIdsWithBackground
        if (hasGroupedBackground && lastWordEnd != null) {
            // AMLL gives the parent paragraph the combined foreground/background duration.
            // Explicit foreground word timing is the vocal boundary in that case.
            return line.endTimeMs?.let { minOf(it, lastWordEnd) } ?: lastWordEnd
        }

        line.endTimeMs?.let { return it }
        lastWordEnd?.let { return it }
        return indexedNextStartByLineId[line.id] ?: trackDurationMs ?: Long.MAX_VALUE
    }

    private fun resolveLineState(
        line: LyricsLine,
        positionMs: Long?,
        lineEndMs: Long,
    ): LineSyncState {
        val wordState = if (positionMs != null) resolveWord(line, lineEndMs, positionMs) else null
        val start = line.startTimeMs
        return LineSyncState(
            line = line,
            activeWord = wordState?.word,
            lineProgress = if (positionMs != null && positionMs >= lineEndMs) {
                1f
            } else {
                progress(positionMs, start, lineEndMs)
            },
            wordProgress = wordState?.progress ?: 0f,
        )
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

