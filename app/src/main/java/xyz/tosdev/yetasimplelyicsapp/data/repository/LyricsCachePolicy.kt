package xyz.tosdev.yetasimplelyicsapp.data.repository

import kotlin.time.Duration
import kotlin.time.Duration.Companion.days

class LyricsCachePolicy(
    val remoteTimeToLive: Duration = 7.days,
) {
    init {
        require(remoteTimeToLive.isPositive()) { "remoteTimeToLive must be positive" }
    }

    fun expiresAt(cachedAtEpochMs: Long, isLocalImport: Boolean): Long {
        if (isLocalImport) return Long.MAX_VALUE
        val ttlMs = remoteTimeToLive.inWholeMilliseconds
        return if (Long.MAX_VALUE - cachedAtEpochMs < ttlMs) {
            Long.MAX_VALUE
        } else {
            cachedAtEpochMs + ttlMs
        }
    }

    fun isFresh(expiresAtEpochMs: Long, nowEpochMs: Long): Boolean =
        expiresAtEpochMs >= nowEpochMs
}
