package xyz.tosdev.yetasimplelyicsapp.domain.lyrics

fun resolveLyricsAlignments(lines: List<LyricsLine>): Map<String, LyricsAlignment> {
    val speakerAlignments = linkedMapOf<String, LyricsAlignment>()
    val speakerOrder = linkedMapOf<String, Int>()

    // Older stored AMLL graphs retained canonical agent IDs, but not the header definitions.
    // When both foreground voices exist, recover that order without using track artists or BG.
    val foregroundIdentities = lines.filterNot(LyricsLine::isBackground)
        .map(LyricsLine::speakerIdentity).toSet()
    if ("v1" in foregroundIdentities && "v2" in foregroundIdentities) {
        speakerOrder["v1"] = 0
        speakerOrder["v2"] = 1
    }

    lines.asSequence()
        .filterNot(LyricsLine::isBackground)
        .forEach { line ->
            val identity = line.speakerIdentity()
            if (identity !in speakerOrder) speakerOrder[identity] = speakerOrder.size
            if (line.alignment != LyricsAlignment.AUTO) {
                speakerAlignments.putIfAbsent(identity, line.alignment)
            }
        }

    speakerOrder.forEach { (identity, order) ->
        speakerAlignments.getOrPut(identity) {
            when (order) {
                0 -> LyricsAlignment.LEFT
                1 -> LyricsAlignment.RIGHT
                else -> if (identity.stableParity() == 0) LyricsAlignment.LEFT else LyricsAlignment.RIGHT
            }
        }
    }

    val foregroundByGroup = linkedMapOf<String, LyricsAlignment>()
    lines.asSequence()
        .filterNot(LyricsLine::isBackground)
        .forEach { line ->
            val groupId = line.groupId ?: return@forEach
            val alignment = if (line.alignment != LyricsAlignment.AUTO) {
                line.alignment
            } else {
                speakerAlignments[line.speakerIdentity()] ?: LyricsAlignment.LEFT
            }
            foregroundByGroup.putIfAbsent(groupId, alignment)
        }

    val resolved = linkedMapOf<String, LyricsAlignment>()
    lines.forEach { line ->
        val alignment = when {
            line.alignment != LyricsAlignment.AUTO -> line.alignment
            line.isBackground && line.groupId != null ->
                foregroundByGroup[line.groupId]
                    ?: speakerAlignments[line.speakerIdentity()]
                    ?: LyricsAlignment.LEFT
            else -> speakerAlignments[line.speakerIdentity()] ?: LyricsAlignment.LEFT
        }
        resolved[line.id] = alignment
    }
    return resolved
}

fun timingProgress(positionMs: Long?, startMs: Long?, endMs: Long?): Float? {
    if (positionMs == null || startMs == null || endMs == null) return null
    if (endMs <= startMs) return if (positionMs >= endMs) 1f else 0f
    return ((positionMs - startMs).toDouble() / (endMs - startMs).toDouble())
        .coerceIn(0.0, 1.0)
        .toFloat()
}

private fun LyricsLine.speakerIdentity(): String = agentId?.trim()?.takeIf(String::isNotEmpty)
    ?: role?.trim()?.takeIf { it.contains("speaker", ignoreCase = true) || it.contains("voice", ignoreCase = true) }
    ?: "__main_voice__"

private fun String.stableParity(): Int = fold(0) { hash, character -> hash * 31 + character.code } and 1
