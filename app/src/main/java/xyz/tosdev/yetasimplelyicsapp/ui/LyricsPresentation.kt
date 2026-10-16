package xyz.tosdev.yetasimplelyicsapp.ui

import xyz.tosdev.yetasimplelyicsapp.domain.lyrics.LyricsDocument
import xyz.tosdev.yetasimplelyicsapp.domain.lyrics.resolveLyricsAlignments

/** Prepared once per document; frame updates never rebuild word lists or text. */
internal fun LyricsDocument.presentationLines(): List<LyricLineUi> {
    val alignments = resolveLyricsAlignments(lines)
    return lines.map { line ->
        LyricLineUi(
            id = line.id,
            words = line.words.map { word ->
                KaraokeWordUi(
                    word.id,
                    word.text,
                    startTimeMs = word.startTimeMs,
                    endTimeMs = word.endTimeMs,
                    segments = word.segments.mapIndexed { index, segment ->
                        KaraokeSegmentUi(
                            id = "${word.id}:segment:$index",
                            text = segment.text,
                            startTimeMs = segment.startTimeMs,
                            endTimeMs = segment.endTimeMs,
                        )
                    },
                )
            },
            originalText = line.text.original,
            translation = line.text.translations.values.firstOrNull(),
            isBackground = line.isBackground,
            startTimeMs = line.startTimeMs,
            endTimeMs = line.endTimeMs,
            alignment = alignments.getValue(line.id),
            sourceAlignment = line.alignment,
            agentId = line.agentId,
            direction = line.direction,
        )
    }
}
