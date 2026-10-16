package xyz.tosdev.yetasimplelyicsapp.widget

import org.junit.Assert.*
import org.junit.Test
import xyz.tosdev.yetasimplelyicsapp.ui.LyricsLoadState
import xyz.tosdev.yetasimplelyicsapp.ui.NowPlayingUiState

class Widgets2StateTest {
    @Test
    fun searchingUnavailableAndErrorsNeverBecomeTheIdleImageForAnActiveSession() {
        val cases = mapOf(LyricsLoadState.SEARCHING to WidgetPresentationState.SEARCHING_LYRICS,
            LyricsLoadState.NOT_FOUND to WidgetPresentationState.LYRICS_UNAVAILABLE,
            LyricsLoadState.ERROR to WidgetPresentationState.ERROR,
            LyricsLoadState.AVAILABLE to WidgetPresentationState.LYRICS_AVAILABLE)
        cases.forEach { (load, expected) ->
            assertEquals(expected, NowPlayingUiState(hasActiveSession = true, lyricsLoadState = load).toWidgetPresentationState())
            assertEquals(WidgetPresentationState.NO_PLAYBACK, NowPlayingUiState(hasActiveSession = false,
                lyricsLoadState = load).toWidgetPresentationState())
        }
    }

    @Test
    fun pausedSongRetainsLyricsAndSessionRemovalRestoresTheIdleImage() {
        val playing = NowPlayingUiState(hasActiveSession = true, isPlaying = true, lyricsLoadState = LyricsLoadState.AVAILABLE)
        assertEquals(playing.toWidgetPresentationState(), playing.copy(isPlaying = false).toWidgetPresentationState())
        assertEquals(WidgetPresentationState.NO_PLAYBACK, playing.copy(hasActiveSession = false).toWidgetPresentationState())
    }

    @Test
    fun changesToPresentationAndIdleImageInvalidateTheRenderWithoutSegmentTicks() {
        val snapshot = LyricsWidgetSnapshot(trackKey = "track", activeWordId = "word", activeWord = "CUANDO",
            hasReliableWordSync = true)
        val config = LyricsWidgetConfig(contentMode = LyricsWidgetContentMode.CURRENT_WORD)
        val signature = snapshot.lineRenderSignature(config)
        assertFalse(shouldRenderWidget(signature, snapshot.copy(capturedAtElapsedMs = 9000), config))
        assertTrue(shouldRenderWidget(signature, snapshot.copy(presentationState = WidgetPresentationState.ERROR), config))
        assertTrue(shouldRenderWidget(signature, snapshot, config.copy(idleImageSource = WidgetIdleImageSource.CUSTOM,
            idleImagePath = "/private/new-image")))
        assertTrue(shouldRenderWidget(signature, snapshot.copy(activeWordId = "next", activeWord = "MIENTRAS"), config))
    }
}
