package xyz.tosdev.yetasimplelyicsapp.widget

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import org.junit.Assert.*
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class WidgetUpdateDispatcherTest {
    private fun word(id: String) = LyricsWidgetSnapshot(trackKey = "test", activeWordId = id,
        activeWord = id, hasReliableWordSync = true)

    @Test fun slowCompositionDropsObsoleteWordsAndDeliversTheCurrentWord() = runTest {
        var current = word("CUANDO")
        val delivered = mutableListOf<String>()
        val composing = CompletableDeferred<Unit>()
        val dispatcher = WidgetUpdateDispatcher(currentSnapshot = { current }, publish = { snapshot, isCurrent ->
            composing.complete(Unit)
            delay(500)
            if (isCurrent()) delivered += snapshot.activeWord
            true
        })
        val job = backgroundScope.launch { dispatcher.run() }
        dispatcher.request()
        composing.await()
        current = word("QUIERE")
        dispatcher.request()
        current = word("MIENTRAS")
        dispatcher.request()
        advanceTimeBy(1_100); runCurrent()
        assertEquals(listOf("MIENTRAS"), delivered)
        job.cancel()
    }

    @Test fun resamplesThePlaybackClockEvenWithoutAnotherQueuedSnapshot() = runTest {
        val delivered = mutableListOf<String>()
        val dispatcher = WidgetUpdateDispatcher(currentSnapshot = {
            if (testScheduler.currentTime < 200) word("BEFORE") else word("NOW")
        }, publish = { snapshot, isCurrent ->
            delay(300)
            if (isCurrent()) delivered += snapshot.activeWord
            true
        })
        backgroundScope.launch { dispatcher.run() }
        dispatcher.request()
        advanceTimeBy(700); runCurrent()
        assertEquals(listOf("NOW"), delivered)
    }

    @Test fun transientFailureRetriesCurrentStateAndLaterRequestsInterruptBackoff() = runTest {
        var current = word("ONE")
        var attempts = 0
        val delivered = mutableListOf<String>()
        val dispatcher = WidgetUpdateDispatcher(currentSnapshot = { current }, publish = { snapshot, _ ->
            attempts++
            if (attempts == 1) false else { delivered += snapshot.activeWord; true }
        })
        backgroundScope.launch { dispatcher.run() }
        dispatcher.request(); runCurrent()
        assertEquals(1, attempts)
        current = word("TWO")
        dispatcher.request(); runCurrent()
        assertEquals(listOf("TWO"), delivered)
        advanceTimeBy(10_000); runCurrent()
        assertEquals(2, attempts)
    }

    @Test fun repeatedRequestsForTheSameSemanticStateDoNotPublishAgain() = runTest {
        var count = 0
        val dispatcher = WidgetUpdateDispatcher(currentSnapshot = { word("SAME") }, publish = { _, _ -> count++; true })
        backgroundScope.launch { dispatcher.run() }
        dispatcher.request(); runCurrent()
        repeat(20) { dispatcher.request() }
        runCurrent()
        assertEquals(1, count)
    }

    @Test fun temporarySnapshotFailureDoesNotKillTheOnlyDispatcher() = runTest {
        var reads = 0
        val delivered = mutableListOf<String>()
        val dispatcher = WidgetUpdateDispatcher(currentSnapshot = {
            if (reads++ == 0) error("temporary model failure")
            word("RECOVERED")
        }, publish = { snapshot, _ -> delivered += snapshot.activeWord; true })
        backgroundScope.launch { dispatcher.run() }
        dispatcher.request(); runCurrent()
        advanceTimeBy(300); runCurrent()
        assertEquals(listOf("RECOVERED"), delivered)
    }

    @Test fun resizeOfAnUnchangedPausedWordIsNotSuppressedBySemanticDeduplication() = runTest {
        var publications = 0
        val refreshed = mutableListOf<Set<Int>>()
        val dispatcher = WidgetUpdateDispatcher(currentSnapshot = { word("PAUSED") },
            publish = { _, _ -> publications++; true },
            refresh = { _, ids, _ -> refreshed += ids; ids })
        backgroundScope.launch { dispatcher.run() }
        dispatcher.request(); runCurrent()
        dispatcher.requestRefresh(setOf(7)); runCurrent()
        assertEquals(1, publications)
        assertEquals(listOf(setOf(7)), refreshed)
    }

    @Test fun refreshFailureOnlyRetriesUndeliveredIdsWithoutAnotherWordEvent() = runTest {
        val attempts = mutableListOf<Set<Int>>()
        val dispatcher = WidgetUpdateDispatcher(currentSnapshot = { word("SAME") },
            publish = { _, _ -> true }, refresh = { _, ids, _ ->
                attempts += ids
                if (attempts.size == 1) setOf(2) else ids
            })
        backgroundScope.launch { dispatcher.run() }
        dispatcher.requestRefresh(setOf(1, 2)); runCurrent()
        advanceTimeBy(300); runCurrent()
        assertEquals(listOf(setOf(1, 2), setOf(1)), attempts)
    }

    @Test fun resizeReceivedDuringDeliveryIsRetainedAfterTheEarlierRefreshCompletes() = runTest {
        val attempts = mutableListOf<Set<Int>>()
        val dispatcher = WidgetUpdateDispatcher(currentSnapshot = { word("SAME") },
            publish = { _, _ -> true }, refresh = { _, ids, _ ->
                attempts += ids
                delay(100)
                ids
            })
        backgroundScope.launch { dispatcher.run() }
        dispatcher.requestRefresh(setOf(7)); runCurrent()
        dispatcher.requestRefresh(setOf(7, 8)); runCurrent()
        advanceTimeBy(500); runCurrent()
        assertEquals(listOf(setOf(7), setOf(7, 8)), attempts)
    }
}
