package xyz.tosdev.yetasimplelyicsapp.ui.components

import xyz.tosdev.yetasimplelyicsapp.ui.MotionLevel

/** Visual light follows the existing measured reveal; it never changes musical time. */
internal fun amllIlluminatedFraction(inkX: Float, boundary: Float, feather: Float): Float =
    ((boundary + feather - inkX) / feather.coerceAtLeast(0.001f)).coerceIn(0f, 1f)

internal fun amllGlowStrength(illumination: Float, active: Boolean, level: MotionLevel): Float =
    if (!active || level == MotionLevel.OFF || level == MotionLevel.REDUCED) 0f
    else illumination.coerceIn(0f, 1f) * 0.22f

/** Only pending ink in a current word-synced row is dimmed; completed ink stays bright. */
internal fun amllKaraokeAlpha(dark: Float, bright: Float, illumination: Float, active: Boolean): Float {
    val pending = if (active) dark * 0.55f else dark
    return pending + (bright - pending) * illumination.coerceIn(0f, 1f)
}
