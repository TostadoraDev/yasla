package xyz.tosdev.yetasimplelyicsapp.media

import org.junit.Assert.assertEquals
import org.junit.Test
import xyz.tosdev.yetasimplelyicsapp.notification.shouldPublishLyricsNotification
import xyz.tosdev.yetasimplelyicsapp.overlay.LockScreenOverlayMode
import xyz.tosdev.yetasimplelyicsapp.overlay.OverlayAlignment
import xyz.tosdev.yetasimplelyicsapp.overlay.OverlayBackground
import xyz.tosdev.yetasimplelyicsapp.overlay.OverlayConfig
import xyz.tosdev.yetasimplelyicsapp.overlay.OverlayLayout
import xyz.tosdev.yetasimplelyicsapp.overlay.OverlayLyricsState
import xyz.tosdev.yetasimplelyicsapp.overlay.OverlayVisibility
import xyz.tosdev.yetasimplelyicsapp.overlay.clampOverlayPosition
import xyz.tosdev.yetasimplelyicsapp.overlay.effectiveBackground
import xyz.tosdev.yetasimplelyicsapp.overlay.migrateLegacyOverlayBackground
import xyz.tosdev.yetasimplelyicsapp.overlay.renderedContentOpacity
import xyz.tosdev.yetasimplelyicsapp.overlay.systemWindowAlpha
import xyz.tosdev.yetasimplelyicsapp.overlay.visibility

class OverlayConfigContractTest {
    @Test
    fun normalizedClampsOpacityAndTextScale() {
        val normalized = OverlayConfig(opacity = 9f, textScale = 0.1f).normalized()

        assertEquals(1f, normalized.opacity)
        assertEquals(0.75f, normalized.textScale)
    }

    @Test
    fun floatingLyricsExposesAllSupportedContentLayouts() {
        assertEquals(
            listOf(
                OverlayLayout.CURRENT_LINE,
                OverlayLayout.CURRENT_AND_NEXT,
                OverlayLayout.PREVIOUS_CURRENT_NEXT,
                OverlayLayout.CURRENT_WORD,
                OverlayLayout.KARAOKE,
                OverlayLayout.DESKTOP_LYRICS,
            ),
            OverlayLayout.entries,
        )
        assertEquals(OverlayLayout.CURRENT_LINE, OverlayConfig().layout)
        assertEquals(OverlayBackground.TRANSPARENT, OverlayConfig().effectiveBackground())
    }

    @Test
    fun legacySubtleDarkDefaultMigratesOnceWithoutRemovingAdvancedChoices() {
        assertEquals(
            OverlayBackground.TRANSPARENT,
            migrateLegacyOverlayBackground(
                storedBackground = OverlayBackground.SUBTLE_DARK,
                migrationComplete = false,
            ),
        )
        assertEquals(
            OverlayBackground.TRANSPARENT,
            migrateLegacyOverlayBackground(
                storedBackground = null,
                migrationComplete = false,
            ),
        )
        assertEquals(
            OverlayBackground.CARD,
            migrateLegacyOverlayBackground(
                storedBackground = OverlayBackground.CARD,
                migrationComplete = false,
            ),
        )
        assertEquals(
            OverlayBackground.SUBTLE_DARK,
            migrateLegacyOverlayBackground(
                storedBackground = OverlayBackground.SUBTLE_DARK,
                migrationComplete = true,
            ),
        )
    }

    @Test
    fun currentWordAlwaysUsesATransparentContainer() {
        val currentWord = OverlayConfig(
            layout = OverlayLayout.CURRENT_WORD,
            background = OverlayBackground.CARD,
            showBorder = true,
            softShadow = true,
        )

        assertEquals(OverlayBackground.TRANSPARENT, currentWord.effectiveBackground())
        assertEquals(
            OverlayBackground.CARD,
            currentWord.copy(layout = OverlayLayout.CURRENT_LINE).effectiveBackground(),
        )
    }

    @Test
    fun systemWindowStaysOpaqueWhileUserOpacityOnlyAffectsRenderedContent() {
        val standard = OverlayConfig(opacity = 0.35f)
        val clickThrough = OverlayConfig(opacity = 1f, clickThrough = true)

        assertEquals(1f, standard.systemWindowAlpha())
        assertEquals(1f, clickThrough.systemWindowAlpha())
        assertEquals(0.35f, standard.renderedContentOpacity())
        assertEquals(0.8f, clickThrough.renderedContentOpacity())
    }

    @Test
    fun overlayLyricsStateKeepsLexicalWordIdentityWhileNormalizingProgress() {
        val normalized = OverlayLyricsState(
            words = listOf("con", "migo"),
            activeWordIndex = 1,
            activeWordId = "word-together",
            wordProgress = 3f,
        ).normalized()

        assertEquals("word-together", normalized.activeWordId)
        assertEquals(1, normalized.activeWordIndex)
        assertEquals(1f, normalized.wordProgress)
    }

    @Test
    fun lockScreenPolicySupportsOffMinimalAndAlways() {
        assertEquals(
            OverlayVisibility.HIDDEN_ON_LOCK_SCREEN,
            OverlayConfig().visibility(isPlaying = true, isKeyguardLocked = true),
        )
        assertEquals(
            OverlayVisibility.MINIMAL_ON_LOCK_SCREEN,
            OverlayConfig(lockScreenMode = LockScreenOverlayMode.MINIMAL)
                .visibility(isPlaying = true, isKeyguardLocked = true),
        )
        assertEquals(
            OverlayVisibility.VISIBLE,
            OverlayConfig(lockScreenMode = LockScreenOverlayMode.ALWAYS)
                .visibility(isPlaying = true, isKeyguardLocked = true),
        )
    }

    @Test
    fun pauseAndKeyguardVisibilityHaveDeterministicPriority() {
        val config = OverlayConfig(hideWhenPaused = true)

        assertEquals(
            OverlayVisibility.HIDDEN_ON_LOCK_SCREEN,
            config.visibility(isPlaying = false, isKeyguardLocked = true),
        )
        assertEquals(
            OverlayVisibility.HIDDEN_WHILE_PAUSED,
            config.visibility(isPlaying = false, isKeyguardLocked = false),
        )
    }

    @Test
    fun lockScreenNotificationIsScopedToKeyguardUnlessAlwaysEnabled() {
        assertEquals(false, shouldPublishLyricsNotification(false, true, false))
        assertEquals(true, shouldPublishLyricsNotification(false, true, true))
        assertEquals(true, shouldPublishLyricsNotification(true, false, false))
    }

    @Test
    fun draggedOverlayIsClampedInsideAvailableArea() {
        assertEquals(
            xyz.tosdev.yetasimplelyicsapp.overlay.OverlayPosition(x = 8, y = 112),
            clampOverlayPosition(
                x = -300,
                y = 900,
                alignment = OverlayAlignment.START,
                availableWidth = 300,
                availableHeight = 200,
                overlayWidth = 220,
                overlayHeight = 80,
                margin = 8,
            ),
        )
    }
}
