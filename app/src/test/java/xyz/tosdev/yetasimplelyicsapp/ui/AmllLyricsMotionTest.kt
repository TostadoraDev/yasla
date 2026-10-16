package xyz.tosdev.yetasimplelyicsapp.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.tosdev.yetasimplelyicsapp.ui.components.AmllSpring
import xyz.tosdev.yetasimplelyicsapp.ui.components.AmllRowMotion
import xyz.tosdev.yetasimplelyicsapp.ui.components.amllApproachAlpha
import xyz.tosdev.yetasimplelyicsapp.ui.components.amllEase
import xyz.tosdev.yetasimplelyicsapp.ui.components.amllEaseOut
import xyz.tosdev.yetasimplelyicsapp.ui.components.amllLineBlur
import xyz.tosdev.yetasimplelyicsapp.ui.components.amllMaskTargets
import xyz.tosdev.yetasimplelyicsapp.ui.components.amllVerticalStiffness
import xyz.tosdev.yetasimplelyicsapp.ui.components.amllWordPose

class AmllLyricsMotionTest {
    @Test
    fun completedWordReversesFromItsAnimationEndAndKeepsDrawingWhileMediaTimeIsPaused() {
        val row = AmllRowMotion(0f)
        row.retarget(0f, true, false, true, 0f, 90f, 15f, 0f, MotionLevel.NORMAL, motionTailMs = 2_600f)
        row.step(0f, 5_000, true, MotionLevel.NORMAL)
        row.retarget(0f, false, false, true, 0f, 90f, 15f, 0f, MotionLevel.NORMAL, motionTailMs = 2_600f)
        row.step(0.1f, 5_000, true, MotionLevel.NORMAL)
        assertEquals(900L, row.baselineElapsed(5_000, 0, 400))
        val before = row.frame.exitMotionMs
        row.step(0.1f, 5_000, true, MotionLevel.NORMAL)
        assertTrue(row.frame.exitMotionMs > before)
        row.step(10f, 5_000, true, MotionLevel.NORMAL)
        assertEquals(2_600f, row.frame.exitMotionMs, 0f)
        val settled = row.frame.exitMotionMs
        row.step(10f, 5_000, true, MotionLevel.NORMAL)
        assertEquals(settled, row.frame.exitMotionMs, 0f)
    }

    @Test
    fun seekClearsThePreviousWordExitClockWithoutSnappingLineMotion() {
        val row = AmllRowMotion(100f)
        row.retarget(0f, true, true, true, 0f, 90f, 15f, 0f, MotionLevel.NORMAL)
        row.step(0.1f, 5_000, true, MotionLevel.NORMAL)
        val position = row.position.position
        val velocity = row.position.velocity
        row.resetWordClock()
        assertEquals(null, row.wordPosition(500))
        assertEquals(null, row.emphasisPosition(500))
        assertEquals(position, row.position.position, 0f)
        assertEquals(velocity, row.position.velocity, 0f)
    }

    @Test
    fun exitingWordReversesOnlyBaselineFloatAndNeverReplaysCompletedEmphasis() {
        val exited = amllWordPose(8_000, 2_000, 40f, "song", false, false,
            MotionLevel.NORMAL, characterIndex = 1, characterCount = 4, baseElapsedMs = 500)
        assertEquals(1f, exited.scale, 0.0001f)
        assertEquals(0f, exited.glowAlpha, 0.0001f)
        // 500 / 2000 = quarter progress, ease-out(0.25) = 0.3781381.
        assertEquals(-0.7562762f, exited.translationYPx, 0.00001f)
    }

    @Test
    fun cssEasingHelpersMatchCapturedQuarterHalfAndThreeQuarterValues() {
        assertEquals(0f, amllEase(0f), 0f)
        assertEquals(0.4085106f, amllEase(0.25f), 0.000001f)
        assertEquals(0.8024034f, amllEase(0.5f), 0.000001f)
        assertEquals(0.9604589f, amllEase(0.75f), 0.000001f)
        assertEquals(1f, amllEase(1f), 0f)

        assertEquals(0f, amllEaseOut(0f), 0f)
        assertEquals(0.3781381f, amllEaseOut(0.25f), 0.000001f)
        assertEquals(0.6846432f, amllEaseOut(0.5f), 0.000001f)
        assertEquals(0.9065353f, amllEaseOut(0.75f), 0.000001f)
        assertEquals(1f, amllEaseOut(1f), 0f)
    }

    @Test
    fun springMatchesCapturedCriticalAndUnderdampedSolutions() {
        val critical = AmllSpring(0f, mass = 1f, stiffness = 100f, damping = 20f)
        critical.retarget(10f)
        assertEquals(2.6424112f, critical.step(0.1f), 0.000001f)

        val underdamped = AmllSpring(0f, mass = 1f, stiffness = 100f, damping = 10f)
        underdamped.retarget(10f)
        assertEquals(3.4029984f, underdamped.step(0.1f), 0.000001f)
    }

    @Test
    fun retargetPreservesVelocityAndDelayedTargetLetsCurrentSpringContinue() {
        val spring = AmllSpring(0f, mass = 1f, stiffness = 100f, damping = 20f)
        spring.retarget(10f)
        spring.step(0.1f)
        val velocityAtRetarget = spring.velocity

        spring.retarget(20f)
        assertEquals(velocityAtRetarget, spring.velocity, 0.000001f)
        assertEquals(20f, spring.target, 0f)
        assertTrue(spring.step(0.01f) > 2.6424112f)

        spring.retarget(-5f, delaySeconds = 0.2f)
        spring.retarget(30f, delaySeconds = 0.2f)
        val beforeDelay = spring.position
        spring.step(0.1f)
        assertEquals(20f, spring.target, 0f)
        assertTrue(spring.position > beforeDelay)
        spring.step(0.1f)
        assertEquals(30f, spring.target, 0f)
    }

    @Test
    fun analyticSpringIsStableAcrossSixtyAndOneHundredTwentyHertzCadence() {
        fun settle(hz: Int): AmllSpring {
            val spring = AmllSpring(0f, mass = 0.9f, stiffness = 90f, damping = 15f)
            spring.retarget(100f)
            repeat(hz) { spring.step(1f / hz) }
            return spring
        }

        val sixty = settle(60)
        val oneTwenty = settle(120)
        assertEquals(sixty.position, oneTwenty.position, 0.0001f)
        assertEquals(sixty.velocity, oneTwenty.velocity, 0.001f)
    }

    @Test
    fun stiffnessMaskAlphaAndBlurMatchViewportRules() {
        assertEquals(220f, amllVerticalStiffness(100L), 0.0001f)
        assertEquals(213.52753f, amllVerticalStiffness(450L), 0.0001f)
        assertEquals(170f, amllVerticalStiffness(800L), 0.0001f)

        val progressive = amllMaskTargets(scale = 0.985f, progressive = true)
        assertEquals(0.6f, progressive.bright, 0.0001f)
        assertEquals(0.3f, progressive.dark, 0.0001f)
        val solid = amllMaskTargets(scale = 0.985f, progressive = false)
        assertEquals(0.3f, solid.bright, 0.0001f)
        assertEquals(0.3f, solid.dark, 0.0001f)

        assertEquals(0f, amllLineBlur(5, 5, 5, isActive = true, compact = false), 0f)
        assertEquals(5f, amllLineBlur(2, 5, 4, isActive = false, compact = false), 0f)
        assertEquals(3.2f, amllLineBlur(8, 5, 4, isActive = false, compact = true), 0.0001f)
        assertEquals(5f, amllLineBlur(30, 5, 4, isActive = false, compact = true), 0f)
    }

    @Test
    fun maskAlphaUsesFastAttackSlowerReleaseAndCloseRangeSnap() {
        assertEquals(0.6405368f, amllApproachAlpha(0.2f, 1f, 0.016f), 0.000001f)
        assertEquals(0.9152361f, amllApproachAlpha(1f, 0.2f, 0.016f), 0.000001f)
        assertEquals(0.2f, amllApproachAlpha(0.2005f, 0.2f, 1f), 0f)
    }

    @Test
    fun shortWordFloatUsesOneSecondMinimumAndHoldsCompletedPose() {
        val halfway = amllWordPose(
            elapsedMs = 500L,
            durationMs = 200L,
            fontSizePx = 40f,
            text = "word",
            isBackground = false,
            isLastWord = false,
            level = MotionLevel.NORMAL,
        )
        val complete = amllWordPose(
            elapsedMs = 1_500L,
            durationMs = 200L,
            fontSizePx = 40f,
            text = "word",
            isBackground = true,
            isLastWord = false,
            level = MotionLevel.NORMAL,
        )

        assertEquals(0f, halfway.translationXPx, 0f)
        assertEquals(-1.3692864f, halfway.translationYPx, 0.00001f)
        assertEquals(1f, halfway.scale, 0f)
        assertEquals(-4f, complete.translationYPx, 0.00001f)
    }

    @Test
    fun eligibleLongWordUsesSampledCharacterEmphasisAtCapturedPhase() {
        val pose = amllWordPose(
            elapsedMs = 1_000L,
            durationMs = 2_000L,
            fontSizePx = 40f,
            text = "song",
            isBackground = false,
            isLastWord = false,
            level = MotionLevel.NORMAL,
            characterIndex = 1,
            characterCount = 4,
        )

        assertEquals(-0.7275532f, pose.translationXPx, 0.00002f)
        assertEquals(-3.9235256f, pose.translationYPx, 0.00002f)
        assertEquals(1.0573413f, pose.scale, 0.000002f)
        assertEquals(1.7777778f, pose.glowRadiusPx, 0.00002f)
        assertEquals(0.1415837f, pose.glowAlpha, 0.000002f)
    }

    @Test
    fun emphasisEligibilityLastWordMultiplierAndMotionLevelsAreExact() {
        val baseline = amllWordPose(
            elapsedMs = 1_000L,
            durationMs = 2_000L,
            fontSizePx = 40f,
            text = "one-letter-too-long",
            isBackground = false,
            isLastWord = false,
            level = MotionLevel.NORMAL,
            characterIndex = 1,
            characterCount = 4,
        )
        assertEquals(1f, baseline.scale, 0f)
        assertEquals(0f, baseline.glowAlpha, 0f)

        val cjk = amllWordPose(
            elapsedMs = 500L,
            durationMs = 1_000L,
            fontSizePx = 40f,
            text = "歌",
            isBackground = false,
            isLastWord = true,
            level = MotionLevel.NORMAL,
            characterIndex = 0,
            characterCount = 1,
        )
        assertTrue(cjk.scale > 1f)
        assertTrue(cjk.glowAlpha > 0f)

        val reduced = amllWordPose(
            elapsedMs = 500L,
            durationMs = 2_000L,
            fontSizePx = 40f,
            text = "song",
            isBackground = true,
            isLastWord = true,
            level = MotionLevel.REDUCED,
            characterIndex = 1,
            characterCount = 4,
        )
        assertEquals(0f, reduced.translationXPx, 0f)
        assertEquals(0f, reduced.translationYPx, 0f)
        assertEquals(1f, reduced.scale, 0f)
        assertEquals(0f, reduced.glowRadiusPx, 0f)
        assertEquals(0f, reduced.glowAlpha, 0f)
    }
}
