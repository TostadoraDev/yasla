package xyz.tosdev.yetasimplelyicsapp.ui.components

import xyz.tosdev.yetasimplelyicsapp.ui.LyricLineUi

internal data class AmllVisualWindow(val startMs: Long, val endMs: Long)

internal data class AmllVisualState(
    val hotIndices: Set<Int>,
    val bufferedIndices: Set<Int>,
    val scrollIndex: Int,
)

/**
 * Keeps AMLL's hot and buffered groups separate. A short overlapping group remains visually
 * active until every buffered group expires, unless a newly-hot group commits a new buffer.
 */
internal class AmllVisualTimeline(private val windows: List<AmllVisualWindow>) {
    private var hotIndices: Set<Int> = emptySet()
    private var bufferedIndices: Set<Int> = emptySet()
    private var scrollIndex = 0

    fun update(
        positionMs: Long?,
        seeking: Boolean = false,
        fallbackHotIndices: Set<Int> = emptySet(),
    ): AmllVisualState {
        val nextHot = if (positionMs == null) {
            fallbackHotIndices.filterTo(linkedSetOf()) { it in windows.indices }
        } else {
            windows.indices.filterTo(linkedSetOf()) { index ->
                val window = windows[index]
                window.startMs <= positionMs && positionMs < window.endMs
            }
        }
        val added = nextHot - hotIndices
        val removedFromBuffer = bufferedIndices - nextHot
        hotIndices = nextHot

        when {
            seeking -> {
                bufferedIndices = nextHot
                scrollIndex = nextHot.minOrNull() ?: positionMs?.let { time ->
                    windows.indexOfFirst { it.startMs >= time }.takeIf { it >= 0 }
                } ?: scrollIndex
            }

            added.isNotEmpty() -> {
                bufferedIndices = (bufferedIndices + added) - removedFromBuffer
                bufferedIndices.minOrNull()?.let { scrollIndex = it }
            }

            removedFromBuffer.isNotEmpty() && removedFromBuffer == bufferedIndices -> {
                bufferedIndices = bufferedIndices.filterTo(linkedSetOf()) { it in nextHot }
            }
        }

        val safeScrollIndex = if (windows.isEmpty()) 0 else scrollIndex.coerceIn(windows.indices)
        return AmllVisualState(
            hotIndices = hotIndices.toSet(),
            bufferedIndices = bufferedIndices.toSet(),
            scrollIndex = safeScrollIndex,
        )
    }
}

internal fun amllLyricsSourceKey(lines: List<LyricLineUi>): List<AmllLineSourceKey> =
    lines.map { line ->
        AmllLineSourceKey(
            id = line.id,
            words = line.words.map { word ->
                AmllWordSourceKey(
                    id = word.id,
                    text = word.text,
                    startTimeMs = word.startTimeMs,
                    endTimeMs = word.endTimeMs,
                    segments = word.segments.map { segment ->
                        AmllSegmentSourceKey(
                            id = segment.id,
                            text = segment.text,
                            startTimeMs = segment.startTimeMs,
                            endTimeMs = segment.endTimeMs,
                        )
                    },
                )
            },
            originalText = line.originalText,
            translation = line.translation,
            isBackground = line.isBackground,
            startTimeMs = line.startTimeMs,
            endTimeMs = line.endTimeMs,
            alignment = line.alignment.name,
            sourceAlignment = line.sourceAlignment.name,
            agentId = line.agentId,
            direction = line.direction.name,
        )
    }

internal data class AmllLineSourceKey(
    val id: String,
    val words: List<AmllWordSourceKey>,
    val originalText: String?,
    val translation: String?,
    val isBackground: Boolean,
    val startTimeMs: Long?,
    val endTimeMs: Long?,
    val alignment: String,
    val sourceAlignment: String,
    val agentId: String?,
    val direction: String,
)

internal data class AmllWordSourceKey(
    val id: String,
    val text: String,
    val startTimeMs: Long?,
    val endTimeMs: Long?,
    val segments: List<AmllSegmentSourceKey>,
)

internal data class AmllSegmentSourceKey(
    val id: String,
    val text: String,
    val startTimeMs: Long?,
    val endTimeMs: Long?,
)

/** Visual focus preroll only. Source lyrics, word timestamps and the sync engine remain intact. */
internal fun amllVisualWindows(lines: List<LyricLineUi>): List<AmllVisualWindow> {
    val raw = lines.map { line ->
        AmllVisualWindow(
            line.words.mapNotNull { it.startTimeMs }.minOrNull() ?: line.startTimeMs ?: Long.MAX_VALUE,
            line.words.mapNotNull { it.endTimeMs }.maxOrNull() ?: line.endTimeMs ?: Long.MIN_VALUE,
        )
    }.toMutableList()
    lines.indices.filter { !lines[it].isBackground }.forEach { index ->
        val background = (index + 1..lines.lastIndex).takeWhile { lines[it].isBackground }
        if (background.isNotEmpty()) {
            val group = listOf(index) + background
            val shared = AmllVisualWindow(group.minOf { raw[it].startMs }, group.maxOf { raw[it].endMs })
            group.forEach { raw[it] = shared }
        }
    }
    val primary = lines.indices.filter { !lines[it].isBackground }
    for (i in 0 until primary.lastIndex) {
        val current = primary[i]
        val next = primary[i + 1]
        val overlap = raw[current].endMs - raw[next].startMs
        val intentional = overlap > 100 && overlap > (raw[next].endMs - raw[next].startMs) * 0.1
        if (overlap > 0 && !intentional) raw[current] = raw[current].copy(endMs = raw[next].startMs)
    }
    var previous: AmllVisualWindow? = null
    var groupStart = 0L
    var groupEnd = 0L
    val result = raw.toMutableList()
    primary.forEach { index ->
        val line = raw[index]
        val prev = previous
        val gap = prev == null || line.startMs >= prev.endMs
        val advance = if (gap) 600L else 400L
        val boundary = if (prev == null) 0L else if (gap) groupEnd else
            prev.startMs + ((prev.endMs - prev.startMs) * 0.3).toLong()
        val start = maxOf(boundary, line.startMs - advance).coerceAtMost(line.startMs)
        result[index] = line.copy(startMs = start)
        (index + 1..lines.lastIndex).takeWhile { lines[it].isBackground }.forEach {
            result[it] = result[index]
        }
        if (prev != null && line.startMs < groupEnd && line.endMs > groupStart) {
            groupStart = minOf(groupStart, line.startMs)
            groupEnd = maxOf(groupEnd, line.endMs)
        } else {
            groupStart = line.startMs
            groupEnd = line.endMs
        }
        previous = line
    }
    return result
}

internal fun amllVisualIntervalMs(
    lines: List<LyricLineUi>,
    windows: List<AmllVisualWindow>,
    index: Int,
): Long? {
    if (index !in lines.indices || index !in windows.indices) return null
    val currentPrimary = if (!lines[index].isBackground) index else
        (index downTo 0).firstOrNull { !lines[it].isBackground } ?: return null
    val previousPrimary = (currentPrimary - 1 downTo 0)
        .firstOrNull { !lines[it].isBackground }
        ?: return null
    return windows[currentPrimary].startMs - windows[previousPrimary].startMs
}
