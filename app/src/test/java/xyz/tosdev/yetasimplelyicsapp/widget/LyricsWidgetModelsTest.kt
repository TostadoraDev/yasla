package xyz.tosdev.yetasimplelyicsapp.widget

import androidx.glance.layout.Alignment
import androidx.glance.text.TextAlign
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale
import xyz.tosdev.yetasimplelyicsapp.domain.lyrics.LyricsWord
import xyz.tosdev.yetasimplelyicsapp.domain.lyrics.LyricsWordSegment

class LyricsWidgetModelsTest {
    @Test
    fun amllSegmentsNeverReplaceTheSemanticWidgetWord() {
        val semanticWord = LyricsWord(
            id = "word-mientras",
            text = "mientras",
            startTimeMs = 1_000L,
            endTimeMs = 1_700L,
            segments = listOf(
                LyricsWordSegment("mien", 1_000L, 1_350L),
                LyricsWordSegment("tras", 1_350L, 1_700L),
            ),
        )

        val duringFirstSnap = resolveStableWidgetWord(semanticWord, 1_175L, listOf(semanticWord))
        val duringSecondSnap = resolveStableWidgetWord(semanticWord, 1_525L, listOf(semanticWord))

        assertEquals("MIENTRAS", duringFirstSnap?.text?.uppercase(Locale.ROOT))
        assertEquals("MIENTRAS", duringSecondSnap?.text?.uppercase(Locale.ROOT))
        assertNotEquals("MIEN", duringFirstSnap?.text?.uppercase(Locale.ROOT))
        assertEquals(duringFirstSnap?.id, duringSecondSnap?.id)
    }

    @Test
    fun everyAmlSegmentCountKeepsOneStableVisibleWord() {
        val cases = listOf(
            "CUANDO" to listOf("cuan", "do"),
            "MIENTRAS" to listOf("mien", "tras"),
            "EXTRAORDINARIO" to listOf("ex", "tra", "or", "di", "na", "rio"),
        )

        cases.forEachIndexed { caseIndex, (expected, segmentTexts) ->
            val segments = segmentTexts.mapIndexed { segmentIndex, text ->
                LyricsWordSegment(
                    text = text,
                    startTimeMs = 1_000L + segmentIndex * 100L,
                    endTimeMs = 1_100L + segmentIndex * 100L,
                )
            }
            val semanticWord = LyricsWord(
                id = "word-$caseIndex",
                text = segmentTexts.joinToString(separator = ""),
                startTimeMs = segments.first().startTimeMs,
                endTimeMs = segments.last().endTimeMs,
                segments = segments,
            )

            segments.forEach { segment ->
                val position = requireNotNull(segment.startTimeMs) + 50L
                val visible = resolveStableWidgetWord(semanticWord, position, listOf(semanticWord))
                assertEquals(expected, visible?.text?.uppercase(Locale.ROOT))
                assertEquals(semanticWord.id, visible?.id)
            }
        }
    }

    @Test
    fun widgetChangesOnlyWhenTheSemanticLyricsWordChanges() {
        val first = timedWord("word-a", "MIENTRAS", 1_000L, 1_700L)
        val second = timedWord("word-b", "CUANDO", 1_700L, 2_100L)

        assertEquals(first, resolveStableWidgetWord(first, 1_350L, listOf(first, second)))
        assertEquals(first, resolveStableWidgetWord(first, 1_525L, listOf(first, second)))
        assertEquals(second, resolveStableWidgetWord(second, 1_800L, listOf(first, second)))
    }

    @Test
    fun oversizedCurrentWordWrapsCompletelyWithoutEllipsis() {
        val layout = layoutWord(
            word = "cuando",
            width = 50f,
            preferred = 32f,
            minimum = 18f,
            maxLines = 2,
        )

        assertEquals("CUA\nNDO", layout.text)
        assertEquals("CUANDO", layout.text.replace("\n", ""))
        assertFalse(layout.ellipsized)
    }

    @Test
    fun requiredCurrentWordsStayCompleteAtSmallMediumAndLargeWidgetWidths() {
        val words = listOf(
            "CUANDO",
            "MIENTRAS",
            "EXTRAORDINARIO",
            "INTERNACIONALIZACION",
            "ELECTROENCEFALOGRAFISTA",
        )
        val widgetSizes = listOf(
            48f to 8,
            96f to 5,
            240f to 3,
        )
        val measurer = WidgetTextMeasurer { text, fontSizeSp ->
            text.length * fontSizeSp * 0.6f
        }

        words.forEach { word ->
            widgetSizes.forEach { (width, maxLines) ->
                val layout = WordLineBreaker.layoutWord(
                    word = word,
                    languageTag = "es",
                    fallbackLocale = Locale.ROOT,
                    availableWidthDp = width,
                    preferredFontSizeSp = 32f,
                    minFontSizeSp = 18f,
                    maxLines = maxLines,
                    textMeasurer = measurer,
                )

                assertFalse("$word at ${width}dp must not ellipsize", layout.ellipsized)
                assertFalse("$word at ${width}dp must not contain three dots", layout.text.contains("..."))
                assertFalse("$word at ${width}dp must not contain ellipsis", layout.text.contains('…'))
                assertEquals(word, layout.visualLines.joinToString(separator = ""))
                assertEquals(layout.lineCount, layout.visualLines.size)
                assertTrue(
                    "$word at ${width}dp must fit every measured line",
                    layout.visualLines.all {
                        measurer.measureWidthDp(it, layout.fontSizeSp) <= width + 0.01f
                    },
                )
            }
        }
    }

    @Test
    fun autoIsTheDefaultContentMode() {
        assertEquals(LyricsWidgetContentMode.AUTO, LyricsWidgetConfig().contentMode)
    }

    @Test
    fun exposesEverySupportedContentMode() {
        assertEquals(
            setOf(
                LyricsWidgetContentMode.AUTO,
                LyricsWidgetContentMode.CURRENT_WORD,
                LyricsWidgetContentMode.CURRENT_LINE,
                LyricsWidgetContentMode.CURRENT_NEXT,
                LyricsWidgetContentMode.PREVIOUS_CURRENT_NEXT,
                LyricsWidgetContentMode.KARAOKE,
                LyricsWidgetContentMode.DESKTOP,
            ),
            LyricsWidgetContentMode.entries.toSet(),
        )
    }

    @Test
    fun preservesEveryPreviousVisualPreset() {
        assertEquals(
            setOf(
                LyricsWidgetLayout.MINIMAL,
                LyricsWidgetLayout.TWO_LINES,
                LyricsWidgetLayout.TRANSPARENT,
                LyricsWidgetLayout.KARAOKE_CARD,
                LyricsWidgetLayout.LARGE,
                LyricsWidgetLayout.COMPACT_PLAYER,
                LyricsWidgetLayout.WORD_FOCUS,
            ),
            LyricsWidgetLayout.entries.toSet(),
        )
    }

    @Test
    fun autoUsesCurrentWordOnlyWithReliableWordSync() {
        val config = LyricsWidgetConfig()

        assertEquals(
            ResolvedWidgetContent.CURRENT_WORD,
            config.resolveContent(snapshot(hasReliableWordSync = true)),
        )
        assertEquals(
            ResolvedWidgetContent.CURRENT_LINE,
            config.resolveContent(snapshot(hasReliableWordSync = false)),
        )
    }

    @Test
    fun autoUsesSyncCapabilityAndActualWidgetSize() {
        val contextual = LyricsWidgetConfig(layout = LyricsWidgetLayout.LARGE)
        val wordSynced = snapshot(
            hasReliableWordSync = true,
            syncType = WidgetLyricsSyncType.WORD,
        )
        val lineSynced = snapshot(
            hasReliableWordSync = false,
            syncType = WidgetLyricsSyncType.LINE,
        )

        assertEquals(
            ResolvedWidgetContent.CURRENT_WORD,
            contextual.resolveContent(wordSynced, WidgetSizeClass.COMPACT),
        )
        assertEquals(
            ResolvedWidgetContent.CURRENT_NEXT,
            contextual.resolveContent(wordSynced, WidgetSizeClass.MEDIUM),
        )
        assertEquals(
            ResolvedWidgetContent.PREVIOUS_CURRENT_NEXT,
            contextual.resolveContent(wordSynced, WidgetSizeClass.EXPANDED),
        )
        assertEquals(
            ResolvedWidgetContent.CURRENT_LINE,
            contextual.resolveContent(lineSynced, WidgetSizeClass.COMPACT),
        )
        assertEquals(
            ResolvedWidgetContent.CURRENT_NEXT,
            contextual.resolveContent(lineSynced, WidgetSizeClass.MEDIUM),
        )
        assertEquals(
            ResolvedWidgetContent.PREVIOUS_CURRENT_NEXT,
            contextual.resolveContent(lineSynced, WidgetSizeClass.EXPANDED),
        )
    }

    @Test
    fun autoKeepsWordFocusAtMediumSizeAndPlainTextUsesLineFallback() {
        val wordFocus = LyricsWidgetConfig(layout = LyricsWidgetLayout.WORD_FOCUS)
        val wordSynced = snapshot(
            hasReliableWordSync = true,
            syncType = WidgetLyricsSyncType.WORD,
        )
        val plain = snapshot(
            hasReliableWordSync = false,
            syncType = WidgetLyricsSyncType.PLAIN,
        )

        assertEquals(
            ResolvedWidgetContent.CURRENT_WORD,
            wordFocus.resolveContent(wordSynced, WidgetSizeClass.MEDIUM),
        )
        WidgetSizeClass.entries.forEach { sizeClass ->
            assertEquals(
                ResolvedWidgetContent.CURRENT_LINE,
                wordFocus.resolveContent(plain, sizeClass),
            )
        }
    }

    @Test
    fun autoRenderSignatureTracksOnlyContentUsedByTheHostSize() {
        val config = LyricsWidgetConfig(layout = LyricsWidgetLayout.LARGE)
        val original = snapshot(
            hasReliableWordSync = true,
            syncType = WidgetLyricsSyncType.WORD,
        )

        val changedContext = original.copy(
            previousLineId = "previous-2",
            nextLineId = "next-2",
        )
        assertEquals(
            original.lineRenderSignature(config, setOf(WidgetSizeClass.COMPACT)),
            changedContext.lineRenderSignature(config, setOf(WidgetSizeClass.COMPACT)),
        )
        assertNotEquals(
            original.lineRenderSignature(config, setOf(WidgetSizeClass.MEDIUM)),
            changedContext.lineRenderSignature(config, setOf(WidgetSizeClass.MEDIUM)),
        )
        assertNotEquals(
            original.lineRenderSignature(config, setOf(WidgetSizeClass.EXPANDED)),
            changedContext.lineRenderSignature(config, setOf(WidgetSizeClass.EXPANDED)),
        )
    }

    @Test
    fun forcedCurrentWordFallsBackToCurrentLine() {
        val config = LyricsWidgetConfig(contentMode = LyricsWidgetContentMode.CURRENT_WORD)

        assertEquals(
            ResolvedWidgetContent.CURRENT_LINE,
            config.resolveContent(snapshot(hasReliableWordSync = false)),
        )
        assertEquals(
            ResolvedWidgetContent.CURRENT_LINE,
            config.resolveContent(
                snapshot(hasReliableWordSync = true).copy(activeWordId = "", activeWord = ""),
            ),
        )
        assertEquals(LyricsWidgetContentMode.CURRENT_WORD, config.contentMode)
    }

    @Test
    fun forcedCurrentLineNeverUsesWordContent() {
        val config = LyricsWidgetConfig(contentMode = LyricsWidgetContentMode.CURRENT_LINE)

        assertEquals(
            ResolvedWidgetContent.CURRENT_LINE,
            config.resolveContent(snapshot(hasReliableWordSync = true)),
        )
    }

    @Test
    fun everyManualModeKeepsItsRequestedContentPolicy() {
        val current = snapshot(hasReliableWordSync = false)
        assertEquals(
            ResolvedWidgetContent.CURRENT_NEXT,
            LyricsWidgetConfig(contentMode = LyricsWidgetContentMode.CURRENT_NEXT)
                .resolveContent(current),
        )
        assertEquals(
            ResolvedWidgetContent.PREVIOUS_CURRENT_NEXT,
            LyricsWidgetConfig(contentMode = LyricsWidgetContentMode.PREVIOUS_CURRENT_NEXT)
                .resolveContent(current),
        )
        assertEquals(
            ResolvedWidgetContent.KARAOKE,
            LyricsWidgetConfig(contentMode = LyricsWidgetContentMode.KARAOKE)
                .resolveContent(current),
        )
        assertEquals(
            ResolvedWidgetContent.DESKTOP,
            LyricsWidgetConfig(contentMode = LyricsWidgetContentMode.DESKTOP)
                .resolveContent(current),
        )
    }

    @Test
    fun currentWordSignatureChangesOnlyAtSemanticWordBoundaries() {
        val config = LyricsWidgetConfig(contentMode = LyricsWidgetContentMode.CURRENT_WORD)
        val original = snapshot(hasReliableWordSync = true)
        val signature = original.lineRenderSignature(config)

        assertEquals(signature, original.copy(capturedAtElapsedMs = 9_000L).lineRenderSignature(config))
        assertEquals(signature, original.copy(activeLineId = "line-2").lineRenderSignature(config))
        assertEquals(signature, original.copy(isPlaying = false).lineRenderSignature(config))
        assertNotEquals(
            signature,
            original.copy(activeWordId = "word-2").lineRenderSignature(config),
        )
        assertNotEquals(
            signature,
            original.copy(activeWordId = "word-2", activeWord = "another")
                .lineRenderSignature(config),
        )
    }

    @Test
    fun currentLineSignatureIgnoresWordChangesAndTracksLineIdentity() {
        val config = LyricsWidgetConfig(contentMode = LyricsWidgetContentMode.CURRENT_LINE)
        val original = snapshot(hasReliableWordSync = true)
        val signature = original.lineRenderSignature(config)

        assertEquals(
            signature,
            original.copy(activeWordId = "word-2", activeWord = "another")
                .lineRenderSignature(config),
        )
        assertNotEquals(
            signature,
            original.copy(activeLineId = "line-2").lineRenderSignature(config),
        )
    }

    @Test
    fun currentNextSignatureTracksOnlyTheDisplayedLinePair() {
        val config = LyricsWidgetConfig(contentMode = LyricsWidgetContentMode.CURRENT_NEXT)
        val original = snapshot()
        val signature = original.lineRenderSignature(config)

        assertEquals(
            signature,
            original.copy(previousLineId = "previous-2", activeWordId = "word-2")
                .lineRenderSignature(config),
        )
        assertNotEquals(
            signature,
            original.copy(nextLineId = "next-2").lineRenderSignature(config),
        )
    }

    @Test
    fun contextSignatureTracksPreviousCurrentAndNextIdentity() {
        val config = LyricsWidgetConfig(
            contentMode = LyricsWidgetContentMode.PREVIOUS_CURRENT_NEXT,
        )
        val original = snapshot()
        val signature = original.lineRenderSignature(config)

        assertNotEquals(
            signature,
            original.copy(previousLineId = "previous-2").lineRenderSignature(config),
        )
        assertNotEquals(
            signature,
            original.copy(nextLineId = "next-2").lineRenderSignature(config),
        )
    }

    @Test
    fun sourceWordRemainsUntouchedWhilePresentationIsUppercase() {
        val source = "dímelo"
        val layout = layoutWord(source, width = 220f)

        assertEquals("dímelo", source)
        assertEquals("DÍMELO", layout.text)
        assertFalse(layout.ellipsized)
    }

    @Test
    fun normalWordUsesOneVisualLineWithoutShrinkingBelowPreference() {
        val layout = layoutWord("mientras", width = 180f)

        assertEquals("MIENTRAS", layout.text)
        assertEquals(1, layout.lineCount)
        assertEquals(28f, layout.fontSizeSp)
    }

    @Test
    fun currentWordShrinksBeforeCreatingASecondLine() {
        val layout = layoutWord(
            word = "cuando",
            width = 65f,
            preferred = 28f,
            minimum = 18f,
            maxLines = 3,
        )

        assertEquals("CUANDO", layout.text)
        assertEquals(1, layout.lineCount)
        assertEquals(18f, layout.fontSizeSp)
        assertFalse(layout.ellipsized)
    }

    @Test
    fun longWordUsesLinguisticBreaksBeforeEllipsis() {
        val layout = layoutWord(
            word = "extraordinariamente",
            width = 120f,
            preferred = 28f,
            minimum = 18f,
            maxLines = 3,
        )

        assertTrue(layout.lineCount in 2..3)
        assertTrue(layout.text.contains('\n'))
        assertFalse(layout.ellipsized)
        assertEquals(
            "EXTRAORDINARIAMENTE",
            layout.text.replace("-\n", "").replace("\n", ""),
        )
    }

    @Test
    fun spanishWordUsesRealSyllableBoundariesWhenMinimumSizeStillDoesNotFit() {
        val layout = layoutWord(
            word = "dobla",
            width = 34f,
            preferred = 28f,
            minimum = 18f,
            maxLines = 3,
        )

        assertEquals("DO\nBLA", layout.text)
        assertEquals(2, layout.lineCount)
        assertFalse(layout.ellipsized)
    }

    @Test
    fun spanishSyllabificationHandlesClustersAndAccents() {
        val locale = Locale.forLanguageTag("es")

        assertEquals(listOf("MIEN", "TRAS"), SyllableBreaker.breakWord("MIENTRAS", locale))
        assertEquals(listOf("CAN", "CIÓN"), SyllableBreaker.breakWord("CANCIÓN", locale))
    }

    @Test
    fun hyphenatedWordKeepsItsOrthographyWhenWrapped() {
        val layout = layoutWord(
            word = "teórico-práctico",
            width = 92f,
            preferred = 28f,
            minimum = 18f,
            maxLines = 3,
        )

        assertFalse(layout.ellipsized)
        assertEquals("TEÓRICO-PRÁCTICO", layout.text.replace("\n", ""))
    }

    @Test
    fun visualWrappingDependsOnlyOnTheSemanticWord() {
        val first = layoutWord("supercalifragilistico", width = 110f)
        val second = layoutWord("supercalifragilistico", width = 110f)

        assertEquals(first, second)
        assertEquals(
            "SUPERCALIFRAGILISTICO",
            first.text.replace("-\n", "").replace("\n", ""),
        )
    }

    @Test
    fun punctuationContractionsAndHyphensRemainIntact() {
        listOf("don't", "I'm", "oh-oh", "pa'l", "¿dónde?").forEach { source ->
            val layout = layoutWord(source, width = 220f)
            assertEquals(source.uppercase(Locale.forLanguageTag("es")), layout.text)
            assertFalse(layout.ellipsized)
        }
    }

    @Test
    fun stableWordResolverKeepsPreviousWordAcrossTimingGap() {
        val first = timedWord("word-a", "DOBLA", 1_000L, 1_500L)
        val second = timedWord("word-b", "MIENTRAS", 1_550L, 1_900L)
        val words = listOf(first, second)

        assertEquals(first, resolveStableWidgetWord(null, 1_525L, words))
        assertEquals(second, resolveStableWidgetWord(null, 1_550L, words))
    }

    @Test
    fun stableWordResolverIsDeterministicForSeeksAndRapidChanges() {
        val first = timedWord("word-a", "UNO", 1_000L, 1_500L)
        val second = timedWord("word-b", "DOS", 1_550L, 1_900L)
        val third = timedWord("word-c", "TRES", 1_910L, 2_100L)
        val words = listOf(first, second, third)

        assertEquals(first, resolveStableWidgetWord(first, 1_200L, words))
        assertEquals(first, resolveStableWidgetWord(null, 1_500L, words))
        assertEquals(third, resolveStableWidgetWord(null, 1_950L, words))
        assertEquals(first, resolveStableWidgetWord(null, 1_300L, words))
    }

    @Test
    fun stableWordResolverNeverReturnsAnEmptyIntermediateAfterFirstWordStarts() {
        val first = timedWord("word-a", "UNO", 1_000L, 1_100L)
        val second = timedWord("word-b", "DOS", 1_500L, 1_600L)
        val words = listOf(first, second)

        (1_000L..1_500L step 25).forEach { position ->
            assertTrue(resolveStableWidgetWord(null, position, words)?.text?.isNotBlank() == true)
        }
    }

    @Test
    fun unknownLanguageUsesCharacterFallbackBeforeEllipsis() {
        val layout = WordLineBreaker.layoutWord(
            word = "extraordinariamente",
            languageTag = "und",
            fallbackLocale = Locale.ROOT,
            availableWidthDp = 80f,
            preferredFontSizeSp = 28f,
            minFontSizeSp = 18f,
            maxLines = 3,
        )

        assertTrue(layout.lineCount in 2..3)
        assertFalse(layout.ellipsized)
        assertEquals("EXTRAORDINARIAMENTE", layout.text.replace("\n", ""))
    }

    @Test
    fun currentWordCanUseThreeCharacterBalancedLines() {
        val layout = WordLineBreaker.layoutWord(
            word = "abcdefghjklm",
            languageTag = "und",
            fallbackLocale = Locale.ROOT,
            availableWidthDp = 44f,
            preferredFontSizeSp = 18f,
            minFontSizeSp = 18f,
            maxLines = 3,
        )

        assertEquals(3, layout.lineCount)
        assertFalse(layout.ellipsized)
        assertEquals("ABCDEFGHJKLM", layout.text.replace("\n", ""))
    }

    @Test
    fun injectedTextMeasurementChoosesCutsFromRenderedWidths() {
        var measurementCount = 0
        val layout = WordLineBreaker.layoutWord(
            word = "wide",
            languageTag = "und",
            fallbackLocale = Locale.ROOT,
            availableWidthDp = 24f,
            preferredFontSizeSp = 18f,
            minFontSizeSp = 18f,
            maxLines = 2,
            textMeasurer = WidgetTextMeasurer { text, _ ->
                measurementCount++
                text.sumOf { if (it == 'W') 20.0 else 8.0 }.toFloat()
            },
        )

        assertEquals("W\nIDE", layout.text)
        assertFalse(layout.ellipsized)
        assertTrue(measurementCount > 0)
    }

    @Test
    fun currentWordPreservesEveryGraphemeEvenAtExtremeWidth() {
        val layout = WordLineBreaker.layoutWord(
            word = "cuando",
            languageTag = "und",
            fallbackLocale = Locale.ROOT,
            availableWidthDp = 1f,
            preferredFontSizeSp = 18f,
            minFontSizeSp = 18f,
            maxLines = 6,
        )

        assertFalse(layout.ellipsized)
        assertEquals("CUANDO", layout.text.replace("\n", ""))
        assertEquals(6, layout.lineCount)
    }

    @Test
    fun currentWordLineCapacityGrowsPastThreeFromAvailableHeight() {
        assertEquals(2, currentWordLineCapacity(20f, 18f, 1f))
        assertEquals(3, currentWordLineCapacity(66f, 18f, 1f))
        assertTrue(currentWordLineCapacity(130f, 18f, 1f) > 3)
    }

    @Test
    fun currentWordBlockAndEveryLineUseCenteredAlignment() {
        assertEquals(Alignment.Center, CURRENT_WORD_BLOCK_ALIGNMENT)
        assertEquals(TextAlign.Center, CURRENT_WORD_TEXT_ALIGNMENT)
    }

    @Test
    fun currentLineWrapsAtWordsAndUsesSeveralVisualLines() {
        val layout = WordLineBreaker.layoutLine(
            line = "Esta es una línea sincronizada completa",
            locale = Locale.forLanguageTag("es"),
            availableWidthDp = 130f,
            availableHeightDp = 120f,
            preferredFontSizeSp = 22f,
            minFontSizeSp = 14f,
            maxLines = 4,
        )

        assertTrue(layout.lineCount in 2..4)
        assertTrue(layout.text.contains('\n'))
        assertFalse(layout.text.contains("ESTA"))
    }

    @Test
    fun currentLineShrinksUntilTheFullTextFitsWidthAndHeight() {
        val source = "Una línea bastante larga que debe conservarse completa"
        val layout = WordLineBreaker.layoutLine(
            line = source,
            locale = Locale.forLanguageTag("es"),
            availableWidthDp = 165f,
            availableHeightDp = 72f,
            preferredFontSizeSp = 28f,
            minFontSizeSp = 11f,
            maxLines = 4,
        )

        assertFalse(layout.ellipsized)
        assertEquals(source, layout.text.replace('\n', ' '))
        assertTrue(layout.fontSizeSp < 28f)
        assertTrue(layout.lineCount <= 4)
    }

    @Test
    fun currentLineUsesEllipsisOnlyWhenMinimumSizeStillCannotFit() {
        val layout = WordLineBreaker.layoutLine(
            line = "supercalifragilistico extra largo sin espacio vertical suficiente",
            locale = Locale.forLanguageTag("es"),
            availableWidthDp = 55f,
            availableHeightDp = 12f,
            preferredFontSizeSp = 22f,
            minFontSizeSp = 12f,
            maxLines = 1,
        )

        assertTrue(layout.ellipsized)
        assertTrue(layout.text.endsWith('…'))
    }

    @Test
    fun configRejectsOpacityOutsidePlatformRange() {
        assertThrows(IllegalArgumentException::class.java) {
            LyricsWidgetConfig(backgroundOpacityPercent = -1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            LyricsWidgetConfig(backgroundOpacityPercent = 101)
        }
    }

    @Test
    fun failedRenderRemainsEligibleForRetryUntilSignatureIsCommitted() {
        val config = LyricsWidgetConfig(contentMode = LyricsWidgetContentMode.CURRENT_LINE)
        val current = snapshot()
        val oldSignature = current.copy(activeLineId = "old-line").lineRenderSignature(config)

        assertTrue(shouldRenderWidget(oldSignature, current, config))
        assertFalse(shouldRenderWidget(current.lineRenderSignature(config), current, config))
    }

    @Test
    fun storedSnapshotPersistsSemanticIdentityButIgnoresClockTicks() {
        val original = snapshot(capturedAtElapsedMs = 1_000L)

        assertNotEquals(
            original.storedSnapshotSignature(),
            original.copy(activeWordId = "word-2").storedSnapshotSignature(),
        )
        assertEquals(
            original.storedSnapshotSignature(),
            original.copy(capturedAtElapsedMs = 9_000L).storedSnapshotSignature(),
        )
    }

    @Test
    fun compactHostDropsSecondaryContentBeforeChangingTextScale() {
        val config = LyricsWidgetConfig(
            layout = LyricsWidgetLayout.LARGE,
            showTranslation = true,
            showPlaybackControls = true,
            textScale = 1.4f,
        )

        val policy = config.renderPolicy(widthDp = 150f, heightDp = 70f)

        assertEquals(WidgetSizeClass.COMPACT, policy.sizeClass)
        assertEquals(LyricsWidgetLayout.MINIMAL, policy.layout)
        assertFalse(policy.showTranslation)
        assertFalse(policy.showPlaybackControls)
        assertEquals(1.4f, config.textScale)
    }

    private fun layoutWord(
        word: String,
        width: Float,
        preferred: Float = 28f,
        minimum: Float = 18f,
        maxLines: Int = 3,
    ) = WordLineBreaker.layoutWord(
        word = word,
        languageTag = "es",
        fallbackLocale = Locale.ROOT,
        availableWidthDp = width,
        preferredFontSizeSp = preferred,
        minFontSizeSp = minimum,
        maxLines = maxLines,
    )

    private fun snapshot(
        capturedAtElapsedMs: Long = 2_000L,
        hasReliableWordSync: Boolean = true,
        syncType: WidgetLyricsSyncType = if (hasReliableWordSync) {
            WidgetLyricsSyncType.WORD
        } else {
            WidgetLyricsSyncType.LINE
        },
    ) = LyricsWidgetSnapshot(
        trackKey = "provider:track",
        title = "Track",
        artist = "Artist",
        previousLineId = "line-0",
        previousLine = "Before",
        activeLineId = "line-1",
        activeLine = "Current line",
        nextLineId = "line-2",
        nextLine = "After",
        translation = "Translated",
        activeWordId = "word-1",
        activeWord = "Current",
        hasReliableWordSync = hasReliableWordSync,
        syncType = syncType,
        lyricsLanguageTag = "es",
        isPlaying = true,
        capturedAtElapsedMs = capturedAtElapsedMs,
    )

    private fun timedWord(
        id: String,
        text: String,
        startTimeMs: Long,
        endTimeMs: Long,
    ) = LyricsWord(
        id = id,
        text = text,
        startTimeMs = startTimeMs,
        endTimeMs = endTimeMs,
    )
}
