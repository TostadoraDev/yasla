package xyz.tosdev.yetasimplelyicsapp.domain.sync

import xyz.tosdev.yetasimplelyicsapp.domain.lyrics.LyricsCapability
import xyz.tosdev.yetasimplelyicsapp.domain.lyrics.LyricsDocument

/** Positive values delay lyrics. A saved zero is a choice, not an absent preference. */
object LyricsOffsetPolicy {
    fun resolve(document: LyricsDocument?, savedOverrideMs: Long?): Long = savedOverrideMs
        ?: if (document?.capabilities?.contains(LyricsCapability.AMLL_TIMING) == true) 250L else 0L
}
